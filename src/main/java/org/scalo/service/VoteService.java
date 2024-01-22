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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@EnableAsync
public class VoteService {
    public static final int TRENDING_AMOUNT = 100;
    public static final double LOOSING_THRESHOLD = -0.4;
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
            exportTrendsNativeQuery(date, filePath.getParent());
        }
    }

    protected void persistVotesParallel(List<Vote> votes) {
        List<List<Vote>> partitioned = ListUtils.partition(votes, processors);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<Vote> part: partitioned) {
            futures.add(CompletableFuture.runAsync(() -> createVotes(part), executorService));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("persist {} votes finished using {} processors", votes.size(), processors);
    }

    protected void persistVotes(List<Vote> votes) {
        createVotes(votes);
    }

    public Double getAverage(String songId, LocalDate since, LocalDate until) {
        Double result = voteRepository.findAverageRatingBySongIdAndVoteDateBetween(songId, since, until);
        log.debug("getAverage = {} for songId {}, since {} until {}", result, songId, since, until);
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
        log.debug("getAveragePrecedingMonths = {} for songId {}, dateFrom {} howManyMonthsBefore {}", result, songId, dateFrom, howManyMonthsBefore);
        return result;
    }

    protected void exportTrends(LocalDate date, Path dirPath) {
        LocalDate month3 = date.minusMonths(2).withDayOfMonth(1);
        List<String> songIds = voteRepository.findDistinctSongIdsByVoteDateBetween(month3, date);

        LocalDate tomorrow = date.plusDays(1);
        Map<String, List<MonthlyAverageDto>> averageRatings = new HashMap<>();
        for (String songId: songIds) {
            averageRatings.put(songId, getAveragePrecedingMonths(songId, tomorrow, 3));
        }
        // TODO: actual calculations
    }

    protected void exportTrendsNativeQuery(LocalDate date, Path dirPath) {
        LocalDate month2 = date.minusMonths(1).withDayOfMonth(1);
        LocalDate month3 = date.minusMonths(2).withDayOfMonth(1);
        List<Object[]> songIdAverageRatingsThreeMonths = voteRepository.findSongIdAverageRatingsByVoteDatesBetween(
                date.withDayOfMonth(1), date,
                month2, month2.withDayOfMonth(month2.lengthOfMonth()),
                month3, month3.withDayOfMonth(month3.lengthOfMonth())
        );

        Function<Object[], ExportDto> arrayToExport = arr -> new ExportDto(
                voteRepository.findFirstBySongId((String) arr[0]).orElseThrow().getSongName(),
                (String) arr[0],
                ((BigDecimal) arr[2]).doubleValue(),
                ((BigDecimal) arr[3]).doubleValue(),
                ((BigDecimal) arr[4]).doubleValue());

        List<ExportDto> trending100 = songIdAverageRatingsThreeMonths.stream()
                .filter(arr -> arr[1] != null && ((BigDecimal) arr[1]).doubleValue() > 0)
                .limit(TRENDING_AMOUNT)
                .map(arrayToExport)
                .collect(Collectors.toList());

        List<ExportDto> loosing = songIdAverageRatingsThreeMonths.stream()
                .filter(arr -> arr[1] != null && ((BigDecimal) arr[1]).doubleValue() < LOOSING_THRESHOLD)
                .map(arrayToExport)
                .collect(Collectors.toList());

        fileService.writeTrendsToFiles(date, dirPath, trending100, loosing);
    }
}
