package org.scalo.data.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class MonthlyAverageDto {
    String month;
    Double avg;
}
