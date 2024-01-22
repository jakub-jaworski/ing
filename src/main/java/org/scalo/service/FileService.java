package org.scalo.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.scalo.data.dto.ExportDto;
import org.scalo.data.entity.Vote;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FileService {
    public static final String PREFIX_TRENDING = "trending100songs-";
    public static final String PREFIX_LOOSING = "songs-loosing-";
    public static final String SUFFIX = ".csv";
    public static final String[] EXPORT_FIELDS = Arrays.stream(ExportDto.class.getDeclaredFields())
            .map(java.lang.reflect.Field::getName)
            .collect(Collectors.toList()).toArray(new String[0]);

    public List<Vote> readVotesFromFile(LocalDate date, Path filePath) {
        List<Vote> votes = new ArrayList<>();
        try (Scanner scanner = new Scanner(filePath)) {
            boolean isFirstLine = true;
            while (scanner.hasNextLine()) {
                String[] fields = scanner.nextLine().split(",");
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                votes.add(new Vote(date, fields[0], fields[1], fields[2], fields[3], fields[4], Integer.valueOf(fields[5]), fields[6]));
            }
        } catch (IOException exception) {
            log.error("While processing " + filePath, exception);
        }
        return votes;
    }

    public void writeTrendsToFiles(LocalDate date, Path dirPath, List<ExportDto> trending100, List<ExportDto> loosing) {
        String dateString = date.format(DateTimeFormatter.ofPattern("yyyyMM"));
        write(Path.of(dirPath.toString(), PREFIX_TRENDING + dateString + SUFFIX), trending100);
        write(Path.of(dirPath.toString(), PREFIX_LOOSING + dateString + SUFFIX), loosing);
    }

    protected void write(Path filePath, List<ExportDto> records) {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath);
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(EXPORT_FIELDS))) {
            for (ExportDto record: records) {
                csvPrinter.printRecord(record.getSong_name(), record.getSong_uuid(), record.getRating_this_month(),
                        record.getRating_previous_month(), record.getRating_2months_back());
            }
            csvPrinter.flush();
        } catch (IOException exception) {
            log.error("While processing " + filePath, exception);
        }
    }
}
