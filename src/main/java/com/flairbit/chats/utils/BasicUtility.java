package com.flairbit.chats.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flairbit.chats.exceptions.BadRequestException;
import com.jayway.jsonpath.JsonPath;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

@Slf4j
@UtilityClass
public final class BasicUtility {

    private static final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final LocalDateTime PG_EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0, 0);

    public static String stringifyObject(Object o) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed stringifying object");
        }
    }


    public static String readSpecificProperty(String body, String prop) {
        try {
            return JsonPath.read(body, prop);
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDomainFromUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    public static List<String> getStringAsList(String ip, String regex) {
        if (ip != null && !ip.isEmpty()) {
            return Arrays.asList(ip.split(regex));
        } else {
            return List.of();
        }
    }

    public static <T> List<List<T>> partitionList(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            batches.add(items.subList(i, Math.min(i + batchSize, items.size())));
        }
        return batches;
    }

    public static String safeExtract(Object obj) {
        return obj != null ? obj.toString() : "";
    }


    public static <T> String safeExtract(T source, Function<T, Object> getter) {
        return source != null ? safeExtract(getter.apply(source)) : "";
    }

    public static <T> T safeParse(String payload, Class<T> clazz) {
        try {
            return om.readValue(payload, clazz);
        } catch (Exception e) {
            log.debug("Failed to parse payload into {}: {}", clazz.getSimpleName(), payload);
            return null;
        }
    }

    public static byte[] parseFileContent(Object rawContent) {
        if (rawContent instanceof byte[] c) {
            return decompress(c);
        } else if (rawContent instanceof String s) {
            byte[] decoded = Base64.getDecoder().decode(s);
            return decompress(decoded);
        }
        throw new BadRequestException("Invalid fileContent type: " + rawContent.getClass());
    }

    private static byte[] decompress(byte[] compressedData) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new BadRequestException("Failed to decompress fileContent: " + e.getMessage());
        }
    }

    public static void writeTimestamp(LocalDateTime timestamp, DataOutputStream out) throws IOException {
        if (timestamp == null) {
            log.error("Null timestamp provided for binary COPY");
            throw new IllegalArgumentException("Timestamp cannot be null");
        }

        out.writeInt(8);

        ZoneOffset offset = ZoneOffset.UTC;
        Instant pgEpochInstant = PG_EPOCH.toInstant(offset);
        Instant tsInstant = timestamp.toInstant(offset);

        long microsSincePgEpoch = Duration.between(pgEpochInstant, tsInstant).toNanos() / 1000;
        out.writeLong(microsSincePgEpoch);
    }

}
