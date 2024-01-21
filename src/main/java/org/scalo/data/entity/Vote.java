package org.scalo.data.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "votes", indexes = {
        @Index(columnList = "songId")
})
@Getter
@Setter
@NoArgsConstructor
public class Vote {
    @Id @GeneratedValue
    Long id;
    LocalDate voteDate;
    String songName;
    String songId;
    String artistName;
    String artistId;
    String userId;
    Integer rating;
    String genre;

    public Vote(LocalDate voteDate, String songName, String songId, String artistName, String artistId, String userId, Integer rating, String genre) {
        this.voteDate = voteDate;
        this.songName = songName;
        this.songId = songId;
        this.artistName = artistName;
        this.artistId = artistId;
        this.userId = userId;
        this.rating = rating;
        this.genre = genre;
    }
    private void setId(Long id) {
    }
}
