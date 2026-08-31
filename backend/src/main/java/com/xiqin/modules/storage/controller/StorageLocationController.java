package com.xiqin.modules.storage.controller;

import com.xiqin.common.result.Result;
import com.xiqin.modules.storage.dto.StorageLocationRequest;
import com.xiqin.modules.storage.dto.StorageLocationVO;
import com.xiqin.modules.storage.service.StorageLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/storage-locations")
@RequiredArgsConstructor
public class StorageLocationController {
    private final StorageLocationService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:view')")
    public Result<List<StorageLocationVO>> list() { return Result.success(service.list()); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:create')")
    public Result<StorageLocationVO> create(@Valid @RequestBody StorageLocationRequest req) { return Result.success(service.create(req)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:edit')")
    public Result<StorageLocationVO> update(@PathVariable Long id, @Valid @RequestBody StorageLocationRequest req) { return Result.success(service.update(id, req)); }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:activate')")
    public Result<StorageLocationVO> activate(@PathVariable Long id) { return Result.success(service.activate(id)); }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:edit')")
    public Result<StorageLocationVO> test(@PathVariable Long id) { return Result.success(service.test(id)); }

    @PostMapping("/{id}/scan")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:scan')")
    public Result<StorageLocationVO> scan(@PathVariable Long id) { return Result.success(service.scan(id)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('storage:delete')")
    public Result<Void> delete(@PathVariable Long id) { service.delete(id); return Result.success(); }
}
