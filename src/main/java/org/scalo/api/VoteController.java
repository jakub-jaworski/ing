package org.scalo.api;

import lombok.extern.slf4j.Slf4j;
import org.scalo.data.dto.MonthlyAverageDto;
import org.scalo.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
public class VoteController {
    private final VoteService voteService;

    @Autowired
    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping("/{songId}/avg")
    public Double avg(
            @PathVariable String songId,
            @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") LocalDate since,
            @RequestParam @DateTimeFormat(pattern = "yyyyMMdd") LocalDate until) {
        log.info("avg for " + songId);
        return voteService.getAverage(songId, since, until);
    }

    @GetMapping("/{songId}/avg-three-months")
    public List<MonthlyAverageDto> avgThreeMonths(@PathVariable String songId) {
        log.info("avgThreeMonths for " + songId);
        return voteService.getAveragePrecedingMonths(songId, LocalDate.now(), 3);
    }
}
