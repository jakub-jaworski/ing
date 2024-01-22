package org.scalo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static org.scalo.service.FileService.SUFFIX;

@Slf4j
@Service
public class FileWatcherService {
    public static final String VOTE_FILE_PREFIX = "tuneheaven-songs-";
    private VoteService voteService;
    private WatchService watchService;
    private Path watchedDir;
    private Thread thread;
    private volatile boolean keepWorking = true;
    @Value("${voteFile.location}")
    private String voteFileLocation;

    @Autowired
    public FileWatcherService(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostConstruct
    public void init() {
        try {
            log.debug("FileWatcherService starting in: {}", voteFileLocation);
            watchService = FileSystems.getDefault().newWatchService();
            watchedDir = Paths.get(voteFileLocation);
            watchedDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            startWatching();
        } catch (IOException exception) {
            log.error("Initializing FileWatcherService", exception);
            throw new IllegalStateException(exception);
        }
    }

    private void startWatching() {
        thread = new Thread(() -> {
            while (keepWorking) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException exception) {
                    continue;
                }

                for (WatchEvent<?> event: key.pollEvents()) {
                    String fileName = event.context().toString();
                    log.debug("File noticed: {}", fileName);
                    if (fileName.startsWith(VOTE_FILE_PREFIX)) {
                        String dateString = fileName.substring(VOTE_FILE_PREFIX.length(), fileName.lastIndexOf(SUFFIX));
                        try {
                            LocalDate localDate = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE);
                            Path fullPath = Path.of(voteFileLocation, fileName);
                            voteService.importVotes(localDate, fullPath);
                            log.info("Vote import started: {}", fullPath);
                        } catch (DateTimeParseException exception) {
                            log.warn("Parsing " + dateString, exception);
                        }
                    }
                }
                key.reset();
            }
        });

        thread.start();
    }

    @PreDestroy
    public void destroy() {
        log.debug("FileWatcherService stopping");
        keepWorking = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException exception) {
                log.error("FileWatcherService closing", exception);
            }
        }
    }
}
