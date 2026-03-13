package com.example.ticket.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import io.helidon.config.Config;
import io.helidon.webserver.testing.junit5.ServerTest;

import com.example.ticket.dto.TicketsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ServerTest
@ExtendWith(MockitoExtension.class)
class VectorServiceTest {

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private DataSource dataSource;

    private VectorService vectorService;

    @BeforeEach
    void setup(Config config) {
        vectorService = new VectorService(embeddingStore, embeddingModel, config, dataSource);
    }

    @Test
    void testUpsertTicket() {

        Long ticketId = 1L;
        String ticketType = "test-type";
        String text = "test-text";
        float[] vector = {1.0f, 2.0f};

        vectorService.upsertTicket(ticketId, ticketType, text, vector);

        verify(embeddingStore, times(1)).removeAll(any(Filter.class));
        verify(embeddingStore, times(1)).add(any(Embedding.class), any(TextSegment.class));
    }

    @Test
    void testDeleteTicket() {
        Long ticketId = 1L;
        vectorService.deleteTicket(ticketId);
        verify(embeddingStore, times(1)).removeAll(any(Filter.class));
    }

    @Test
    void testDeleteAllTicketsSuccess() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(statement);

        vectorService.deleteAllTickets();

        verify(statement, times(1)).execute();
    }

    @Test
    void testDeleteAllTicketsTruncateFails() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.execute()).thenThrow(new SQLException("Truncate failed"));

        vectorService.deleteAllTickets();

        verify(statement, times(1)).execute();
        verify(connection, times(2)).prepareStatement(any());
    }

    @Test
    void testDeleteAllTicketsDeleteFails() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.execute()).thenThrow(new SQLException("Truncate failed"));
        when(statement.executeUpdate()).thenThrow(new SQLException("Delete failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, vectorService::deleteAllTickets);
        assertEquals("Failed to delete all tickets from the database", exception.getMessage());
    }

    @Test
    void testSearchSimilar() {
        String queryText = "test query";
        int maxResults = 5;
        double minScore = 0.5;
        Long excludeTicketId = 1L;

        Embedding embedding = new Embedding(new float[] {1.0f, 2.0f});
        when(embeddingModel.embed(queryText)).thenReturn(Response.from(embedding));
        var result = mock(EmbeddingSearchResult.class);
        when(embeddingStore.search(any())).thenReturn(result);

        vectorService.searchSimilar(queryText, maxResults, minScore, excludeTicketId);

        verify(embeddingModel, times(1)).embed(queryText);
        verify(embeddingStore, times(1)).search(any());
    }

    @Test
    void testRetrieveAllTickets() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);

        vectorService.retrieveAllTickets();

        verify(statement, times(1)).executeQuery();
    }

    @Test
    void testRetrieveAllTicketsFails() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenThrow(new SQLException("Query failed"));

        List<TicketsResponse.TicketInfo> result = vectorService.retrieveAllTickets();

        assertTrue(result.isEmpty());
    }
}
