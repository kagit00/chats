package com.flairbit.chats.repo;

import com.flairbit.chats.dto.ProfileChatDto;
import com.flairbit.chats.dto.UserDTO;
import com.flairbit.chats.models.ChatMessage;
import com.flairbit.chats.models.ChatSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ChatMessageJDBCRepository {

    private final JdbcTemplate jdbc;


    private static final String SQL_UPSERT_MESSAGE = """
        INSERT INTO chat_messages
        (id, session_id, sender_profile_id, content, delivered, seen, sent_at, client_msg_id)
        VALUES (gen_random_uuid(), ?, ?, ?, false, false, ?, ?)
        ON CONFLICT (client_msg_id) DO NOTHING
        RETURNING id, session_id, sender_profile_id, content, delivered, seen, sent_at, client_msg_id
        """;

    public ChatMessage save(ChatMessage msg) {
        return jdbc.query(SQL_UPSERT_MESSAGE, rs -> {
                    if (!rs.next()) {
                        log.info("Duplicate message ignored: {}", msg.getClientMsgId());
                        return findByClientMsgId(msg.getClientMsgId())
                                .orElseThrow(() -> new IllegalStateException("Message not found after conflict"));
                    }
                    return mapRow(rs, 1);
                },
                msg.getSession().getId(),
                msg.getSenderProfileId(),
                msg.getContent(),
                Timestamp.from(msg.getSentAt()),
                msg.getClientMsgId()
        );
    }


    private static final String SQL_FIND_BY_CLIENT_MSG_ID = """
        SELECT id, session_id, sender_profile_id, content, delivered, seen, sent_at, client_msg_id
        FROM chat_messages
        WHERE client_msg_id = ?
        """;

    public Optional<ChatMessage> findByClientMsgId(UUID clientMsgId) {
        List<ChatMessage> results = jdbc.query(
                SQL_FIND_BY_CLIENT_MSG_ID,
                new ChatMessageRowMapper(),
                clientMsgId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private static final String SQL_EXISTS_BY_CLIENT_MSG_ID = """
        SELECT COUNT(*) FROM chat_messages WHERE client_msg_id = ?
        """;

    public boolean existsByClientMsgId(UUID clientMsgId) {
        Integer count = jdbc.queryForObject(SQL_EXISTS_BY_CLIENT_MSG_ID, Integer.class, clientMsgId);
        return count != null && count > 0;
    }


    private static final String SQL_FIND_BY_ID = """
        SELECT
            m.id, m.session_id, m.sender_profile_id, m.content, m.delivered, m.seen, m.sent_at, m.client_msg_id,
            s.id as s_id,
            p.id as p_id, p.display_name as p_display_name,
            u.id as u_id, u.email as u_email, u.username as u_username
        FROM chat_messages m
        JOIN chat_sessions s ON m.session_id = s.id
        JOIN profiles p ON m.sender_profile_id = p.id
        JOIN users u ON p.user_id = u.id
        WHERE m.id = ?
        """;

    public Optional<ChatMessage> findById(UUID id) {
        List<ChatMessage> results = jdbc.query(SQL_FIND_BY_ID, new ChatMessageFullRowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }


    private static final String SQL_FIND_BY_SESSION_ID = """
        SELECT
            m.id, m.session_id, m.sender_profile_id, m.content, m.delivered, m.seen, m.sent_at, m.client_msg_id,
            p.id as p_id, p.display_name as p_display_name,
            u.id as u_id, u.email as u_email, u.username as u_username
        FROM chat_messages m
        JOIN profiles p ON m.sender_profile_id = p.id
        JOIN users u ON p.user_id = u.id
        WHERE m.session_id = ?
        ORDER BY m.sent_at DESC
        LIMIT ?
        """;

    public List<ChatMessage> findBySessionIdOrderBySentAtDesc(UUID sessionId, int limit) {
        return jdbc.query(
                SQL_FIND_BY_SESSION_ID,
                new ChatMessageWithProfileRowMapper(),
                sessionId, limit
        );
    }


    private static final String SQL_FIND_UNSEEN = """
        SELECT
            m.id, m.session_id, m.sender_profile_id, m.content, m.delivered, m.seen, m.sent_at, m.client_msg_id
        FROM chat_messages m
        WHERE m.session_id = ?
          AND m.seen = false
          AND m.sender_profile_id != ?
        """;

    public List<ChatMessage> findUnseenBySessionAndReader(UUID sessionId, UUID readerProfileId) {
        return jdbc.query(
                SQL_FIND_UNSEEN,
                new ChatMessageRowMapper(),
                sessionId, readerProfileId
        );
    }

    private static class ChatMessageRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage msg = new ChatMessage();
            msg.setId(rs.getObject("id", UUID.class));
            msg.setSession(ChatSession.builder().id(rs.getObject("session_id", UUID.class)).build());
            msg.setSenderProfileId(rs.getObject("sender_profile_id", UUID.class));
            msg.setContent(rs.getString("content"));
            msg.setDelivered(rs.getBoolean("delivered"));
            msg.setSeen(rs.getBoolean("seen"));
            msg.setSentAt(rs.getTimestamp("sent_at").toInstant());
            msg.setClientMsgId(rs.getObject("client_msg_id", UUID.class));
            return msg;
        }
    }

    private static class ChatMessageWithProfileRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage msg = new ChatMessage();
            msg.setId(rs.getObject("id", UUID.class));
            msg.setSession(ChatSession.builder().id(rs.getObject("session_id", UUID.class)).build());
            msg.setSenderProfileId(rs.getObject("sender_profile_id", UUID.class));
            msg.setContent(rs.getString("content"));
            msg.setDelivered(rs.getBoolean("delivered"));
            msg.setSeen(rs.getBoolean("seen"));
            msg.setSentAt(rs.getTimestamp("sent_at").toInstant());
            msg.setClientMsgId(rs.getObject("client_msg_id", UUID.class));

            ProfileChatDto profileDto = new ProfileChatDto(
                    rs.getObject("p_id", UUID.class),
                    rs.getString("p_display_name"),
                    rs.getString("u_email"),
                    rs.getObject("u_id", UUID.class)
            );
            UserDTO userDto = new UserDTO(
                    rs.getObject("u_id", UUID.class),
                    rs.getString("u_email"),
                    rs.getString("u_username")
            );
            return msg;
        }
    }

    private static class ChatMessageFullRowMapper implements RowMapper<ChatMessage> {
        @Override
        public ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChatMessage msg = new ChatMessage();
            msg.setId(rs.getObject("id", UUID.class));
            msg.setSession(ChatSession.builder().id(rs.getObject("s_id", UUID.class)).build());
            msg.setSenderProfileId(rs.getObject("sender_profile_id", UUID.class));
            msg.setContent(rs.getString("content"));
            msg.setDelivered(rs.getBoolean("delivered"));
            msg.setSeen(rs.getBoolean("seen"));
            msg.setSentAt(rs.getTimestamp("sent_at").toInstant());
            msg.setClientMsgId(rs.getObject("client_msg_id", UUID.class));
            return msg;
        }
    }

    private ChatMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ChatMessageRowMapper().mapRow(rs, rowNum);
    }
}