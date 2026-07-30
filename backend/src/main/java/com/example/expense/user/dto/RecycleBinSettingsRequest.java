package com.example.expense.user.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigInteger;

public record RecycleBinSettingsRequest(
        @NotNull(message = "保留天数不能为空")
        @Min(value = 1, message = "保留天数不能少于 1 天")
        @Max(value = 365, message = "保留天数不能超过 365 天")
        @JsonDeserialize(using = RetentionDaysDeserializer.class)
        Integer retentionDays
) {
    public static class RetentionDaysDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return null;
            }
            BigInteger value = parser.getBigIntegerValue();
            if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                    || value.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
                return null;
            }
            return value.intValue();
        }
    }
}
