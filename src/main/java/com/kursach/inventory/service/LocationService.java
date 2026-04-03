package com.kursach.inventory.service;

import com.kursach.inventory.domain.Location;
import com.kursach.inventory.repo.LocationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {
    private final LocationRepository locationRepository;
    private final ActionLogService logService;

    public LocationService(LocationRepository locationRepository, ActionLogService logService) {
        this.locationRepository = locationRepository;
        this.logService = logService;
    }

    public List<Location> listAll() {
        return locationRepository.findAll(Sort.by("name"));
    }

    public Location getById(Long id) {
        return locationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Локация не найдена"));
    }

    @Transactional
    public Location save(Location location, String actor) {
        Location saved = locationRepository.save(location);
        logService.log(actor, "Сохранена локация " + saved.getName());
        return saved;
    }

    @Transactional
    public Location rename(Long id, String name, String actor) {
        Location location = getById(id);
        location.setName(name);
        Location saved = locationRepository.save(location);
        logService.log(actor, "Обновлено название локации " + saved.getName());
        return saved;
    }

    @Transactional
    public void delete(Long id, String actor) {
        Location location = getById(id);
        locationRepository.delete(location);
        logService.log(actor, "Удалена локация " + location.getName());
    }
}
