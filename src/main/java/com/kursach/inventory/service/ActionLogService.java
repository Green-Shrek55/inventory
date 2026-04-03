package com.kursach.inventory.service;

import com.kursach.inventory.domain.ActionLog;
import com.kursach.inventory.repo.ActionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActionLogService {
    private final ActionLogRepository repo;

    public ActionLogService(ActionLogRepository repo) {
        this.repo = repo;
    }

    public void log(String actor, String message) {
        repo.save(new ActionLog(actor == null ? "system" : actor, message));
    }

    public List<ActionLog> latest(int limit) {
        return repo.findAll(PageRequest.of(0, Math.max(1, limit),
                Sort.by(Sort.Direction.DESC, "ts"))).getContent();
    }

    public List<ActionLog> findAll() {
        return repo.findAll(Sort.by(Sort.Direction.DESC, "ts"));
    }

    public Page<ActionLog> search(String actorFilter, String textFilter, int page, int size) {
        String actor = (actorFilter == null) ? "" : actorFilter.trim();
        String text = (textFilter == null) ? "" : textFilter.trim();
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "ts"));
        return repo.findByActorContainingIgnoreCaseAndMessageContainingIgnoreCase(actor, text, pageable);
    }

    public List<String> actors() {
        return repo.findDistinctActors();
    }
}
