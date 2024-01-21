package org.scalo.data.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ExportDto {
    String song_name;
    String song_uuid;
    Double rating_this_month;
    Double rating_previous_month;
    Double rating_2months_back;
}
