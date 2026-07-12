package com.yeonwoo.askwiki.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EsClientConfig {

    /** RestClient connects lazily, so Elasticsearch being unavailable does not prevent application startup. */
    @Bean
    public ElasticsearchClient elasticsearchClient(@Value("${askwiki.es.url}") String url) {
        RestClient restClient = RestClient.builder(HttpHost.create(url)).build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}
