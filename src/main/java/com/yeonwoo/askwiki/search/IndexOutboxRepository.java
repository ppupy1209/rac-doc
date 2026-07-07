package com.yeonwoo.askwiki.search;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IndexOutboxRepository extends JpaRepository<IndexOutboxEvent, Long> {

    List<IndexOutboxEvent> findByStatusOrderByIdAsc(IndexOutboxEvent.Status status);
}
