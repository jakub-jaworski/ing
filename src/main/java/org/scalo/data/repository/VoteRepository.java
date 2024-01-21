package org.scalo.data.repository;

import org.scalo.data.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, String> {
    List<Vote> findBySongIdAndVoteDateBetween(String songId, LocalDate since, LocalDate until);

    @Query("SELECT AVG(rating) FROM Vote WHERE songId = :songId AND voteDate BETWEEN :since AND :until")
    Double findAverageRatingBySongIdAndVoteDateBetween(String songId, LocalDate since, LocalDate until);

    @Query("SELECT DISTINCT songId FROM Vote WHERE voteDate BETWEEN :since AND :until")
    List<String> findDistinctSongIdsByVoteDateBetween(LocalDate since, LocalDate until);

    @Query(value = "SELECT song_id, " +
            "(AVG(rating) FILTER (WHERE vote_date BETWEEN :monthBegin AND :monthEnd) - " +
            "AVG(rating) FILTER (WHERE vote_date BETWEEN :monthPrevBegin AND :monthPrevEnd)) AS increase, " +
            "AVG(rating) FILTER (WHERE vote_date BETWEEN :monthBegin AND :monthEnd) AS month1, " +
            "AVG(rating) FILTER (WHERE vote_date BETWEEN :monthPrevBegin AND :monthPrevEnd) AS month2, " +
            "AVG(rating) FILTER (WHERE vote_date BETWEEN :monthPrevPrevBegin AND :monthPrevPrevEnd) AS month3 " +
            "FROM votes " +
            "GROUP BY song_id " +
            "ORDER BY increase DESC, month1 DESC, song_id", nativeQuery = true)
    List<Object[]> findSongIdAverageRatingsByVoteDatesBetween(LocalDate monthBegin, LocalDate monthEnd,
                                                              LocalDate monthPrevBegin, LocalDate monthPrevEnd,
                                                              LocalDate monthPrevPrevBegin, LocalDate monthPrevPrevEnd);
    Optional<Vote> findFirstBySongId(String songId);
}
