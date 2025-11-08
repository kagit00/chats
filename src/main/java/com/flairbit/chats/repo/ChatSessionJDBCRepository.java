package com.flairbit.chats.repo;


import com.flairbit.chats.models.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;


@Repository
@RequiredArgsConstructor
@Slf4j
public class ChatSessionJDBCRepository {

    private final JdbcTemplate jdbc;

    private static final String SQL_FIND_BY_ID = """
        SELECT id, sender_profile_id, receiver_profile_id, intent, created_at
        FROM chat_sessions
        WHERE id = ?
        """;

    private static final String SQL_FIND_BY_PARTICIPANTS = """
        SELECT id, sender_profile_id, receiver_profile_id, intent, created_at
        FROM chat_sessions
        WHERE (sender_profile_id = ? AND receiver_profile_id = ?)
           OR (sender_profile_id = ? AND receiver_profile_id = ?)
        AND intent = ?
        """;

    private static final String SQL_UPSERT = """
        INSERT INTO chat_sessions (id, sender_profile_id, receiver_profile_id, intent, created_at)
        VALUES (gen_random_uuid(), ?, ?, ?, now())
        ON CONFLICT (sender_profile_id, receiver_profile_id, intent) DO NOTHING
        RETURNING id, sender_profile_id, receiver_profile_id, intent, created_at
        """;

    public Optional<ChatSession> findById(UUID sessionId) {
        return jdbc.query(SQL_FIND_BY_ID, new ChatSessionRowMapper(), sessionId)
                .stream().findFirst();
    }

    public ChatSession getOrCreate(UUID profileA, UUID profileB, String intent) {
        UUID sender = profileA.compareTo(profileB) <= 0 ? profileA : profileB;
        UUID receiver = profileA.compareTo(profileB) <= 0 ? profileB : profileA;

        List<ChatSession> existing = jdbc.query(
                SQL_FIND_BY_PARTICIPANTS,
                new ChatSessionRowMapper(),
                sender, receiver, receiver, sender, intent
        );

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        return jdbc.queryForObject(
                SQL_UPSERT,
                new ChatSessionRowMapper(),
                sender, receiver, intent
        );
    }

    private static class ChatSessionRowMapper implements RowMapper<ChatSession> {
        @Override
        public ChatSession mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ChatSession.builder()
                    .id(rs.getObject("id", UUID.class))
                    .senderProfileId(rs.getObject("sender_profile_id", UUID.class))
                    .receiverProfileId(rs.getObject("receiver_profile_id", UUID.class))
                    .intent(rs.getString("intent"))
                    .createdAt(rs.getTimestamp("created_at").toInstant())
                    .build();
        }
    }
}