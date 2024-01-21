package org.scalo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.scalo.data.dto.ExportDto;
import org.scalo.data.dto.MonthlyAverageDto;
import org.scalo.data.entity.Vote;
import org.scalo.data.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@EnableAsync
public class VoteService {
    private final int processors = Runtime.getRuntime().availableProcessors();
    private ExecutorService executorService = Executors.newFixedThreadPool(processors);
    private final VoteRepository voteRepository;
    private final FileService fileService;

    @Autowired
    public VoteService(VoteRepository voteRepository, FileService fileService) {
        this.voteRepository = voteRepository;
        this.fileService = fileService;
    }

    public List<Vote> createVotes(Iterable<Vote> votes) {
        return voteRepository.saveAll(votes);
    }

    @Async
    public void importVotes(LocalDate date, Path filePath) {
        List<Vote> votes = fileService.readVotesFromFile(date, filePath);
        persistVotesParallel(votes);

        if (date.equals(date.withDayOfMonth(date.lengthOfMonth()))) {
            exportTrendsNative(date, filePath.getParent());
        }
    }

    protected void persistVotesParallel(List<Vote> votes) {
        log.info("persistVotes started for " + votes.size() + " votes");
        List<List<Vote>> partitioned = ListUtils.partition(votes, processors);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Vote> part: partitioned) {
            futures.add(CompletableFuture.runAsync(() -> createVotes(part), executorService));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("persistVotes finished using " + processors + " processors");
    }
    protected void persistVotes(List<Vote> votes) {
        createVotes(votes);
    }

    public Double getAverage(String songId, LocalDate since, LocalDate until) {
        Double result = voteRepository.findAverageRatingBySongIdAndVoteDateBetween(songId, since, until);
        log.info("getAverage = " + result + " for " + songId + ", since " + since + " until " + until);
        return result;
    }

    public List<MonthlyAverageDto> getAveragePrecedingMonths(String songId, LocalDate dateFrom, int howManyMonthsBefore) {
        List<MonthlyAverageDto> result = new ArrayList<>();
        LocalDate processedDate = dateFrom.minusMonths(howManyMonthsBefore);
        for (int i = 0; i < howManyMonthsBefore; i++) {
            String month = processedDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
            Double average = voteRepository.findAverageRatingBySongIdAndVoteDateBetween(songId, processedDate.withDayOfMonth(1), processedDate.withDayOfMonth(processedDate.lengthOfMonth()));
            result.add(new MonthlyAverageDto(month, average));
            processedDate = processedDate.plusMonths(1);
        }
        //log.info("getAverage = " + result + " for " + songId + ", dateFrom " + dateFrom + ", howManyMonthsBefore " + howManyMonthsBefore);
        return result;
    }

    protected void exportTrends(LocalDate date, Path dirPath) {
        log.info("exportTrends for " + date + " to " + dirPath);
        LocalDate month3 = date.minusMonths(2).withDayOfMonth(1);
        List<String> songIds = voteRepository.findDistinctSongIdsByVoteDateBetween(month3, date);

        LocalDate tomorrow = date.plusDays(1);
        Map<String, List<MonthlyAverageDto>> averageRatings = new HashMap<>();
        for (String songId: songIds) {
            averageRatings.put(songId, getAveragePrecedingMonths(songId, tomorrow, 3));
        }
        //log.info("averageRatings = " + averageRatings.entrySet().stream().map(e -> "\n" + e.getKey() + " = " + e.getValue()).collect(Collectors.toList()));
        // TODO: actual calculations
    }

    @Async
    protected void exportTrendsNative(LocalDate date, Path dirPath) {
        log.info("exportTrendsNative for " + date + " to " + dirPath);
        LocalDate month2 = date.minusMonths(1).withDayOfMonth(1);
        LocalDate month3 = date.minusMonths(2).withDayOfMonth(1);
        List<Object[]> songIdAverageRatingsThreeMonths = voteRepository.findSongIdAverageRatingsByVoteDatesBetween(
                date.withDayOfMonth(1), date,
                month2, month2.withDayOfMonth(month2.lengthOfMonth()),
                month3, month3.withDayOfMonth(month3.lengthOfMonth())
        );

//        for (Object[] by: songIdAverageRatingsThreeMonths) {
//            log.info("songId = " + by[0]
//                    + ", " + (by[1] == null ? "null" : ((BigDecimal) by[1]).doubleValue())
//                    + ", " + (by[2] == null ? "null" : ((BigDecimal) by[2]).doubleValue())
//                    + ", " + (by[3] == null ? "null" : ((BigDecimal) by[3]).doubleValue())
//                    + ", " + (by[4] == null ? "null" : ((BigDecimal) by[4]).doubleValue())
//            );
//        }
//
        List<ExportDto> trending100 = songIdAverageRatingsThreeMonths.stream()
                .filter(arr -> arr[1] != null && ((BigDecimal) arr[1]).doubleValue() > 0)
                .limit(100)
                .map(arr -> new ExportDto(
                        voteRepository.findFirstBySongId((String) arr[0]).orElseThrow().getSongName(),
                        (String) arr[0],
                        ((BigDecimal) arr[2]).doubleValue(),
                        ((BigDecimal) arr[3]).doubleValue(),
                        ((BigDecimal) arr[4]).doubleValue()))
                .collect(Collectors.toList());
        log.info("trending100 = " + trending100.stream().map(e -> "\n" + e.toString()).collect(Collectors.toList()));

        List<ExportDto> loosing = songIdAverageRatingsThreeMonths.stream()
                .filter(arr -> arr[1] != null && ((BigDecimal) arr[1]).doubleValue() < -0.4)
                .map(arr -> new ExportDto(
                        voteRepository.findFirstBySongId((String) arr[0]).orElseThrow().getSongName(),
                        (String) arr[0],
                        ((BigDecimal) arr[2]).doubleValue(),
                        ((BigDecimal) arr[3]).doubleValue(),
                        ((BigDecimal) arr[4]).doubleValue()))
                .collect(Collectors.toList());
        log.info("loosing = " + loosing.stream().map(e -> "\n" + e.toString()).collect(Collectors.toList()));
        fileService.writeTrendsToFiles(date, dirPath, trending100, loosing);
    }
}
