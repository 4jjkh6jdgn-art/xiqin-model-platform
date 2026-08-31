package com.xiqin.modules.storage.service;

import com.xiqin.common.exception.BusinessException;
import com.xiqin.modules.storage.dto.StorageLocationRequest;
import com.xiqin.modules.storage.dto.StorageLocationVO;
import com.xiqin.modules.storage.entity.StorageLocation;
import com.xiqin.modules.storage.repository.StorageLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class StorageLocationService {
    private final StorageLocationRepository repository;
    private final StorageService storageService;

    public List<StorageLocationVO> list() {
        return repository.findAllByOrderByCurrentDescCreatedAtAsc().stream().map(this::toVO).toList();
    }

    @Transactional
    public StorageLocationVO create(StorageLocationRequest req) {
        StorageLocation location = StorageLocation.builder()
                .name(req.getName().trim()).type(req.getType().toUpperCase(Locale.ROOT))
                .address(req.getAddress().trim()).mountPath(clean(req.getMountPath()))
                .username(clean(req.getUsername())).credentialSecret(clean(req.getCredentialSecret()))
                .status("unknown").build();
        return toVO(repository.save(location));
    }

    @Transactional
    public StorageLocationVO update(Long id, StorageLocationRequest req) {
        StorageLocation location = get(id);
        location.setName(req.getName().trim());
        location.setType(req.getType().toUpperCase(Locale.ROOT));
        location.setAddress(req.getAddress().trim());
        location.setMountPath(clean(req.getMountPath()));
        location.setUsername(clean(req.getUsername()));
        if (req.getCredentialSecret() != null && !req.getCredentialSecret().isBlank()) {
            location.setCredentialSecret(req.getCredentialSecret());
        }
        location.setStatus("unknown");
        location.setLastError(null);
        location.setUpdatedAt(LocalDateTime.now());
        return toVO(repository.save(location));
    }

    @Transactional
    public StorageLocationVO activate(Long id) {
        StorageLocation selected = get(id);
        verify(selected);
        repository.findByCurrentTrue().ifPresent(current -> {
            if (!current.getId().equals(id)) {
                current.setCurrent(false);
                current.setUpdatedAt(LocalDateTime.now());
                repository.save(current);
            }
        });
        selected.setCurrent(true);
        selected.setStatus("online");
        selected.setLastError(null);
        selected.setUpdatedAt(LocalDateTime.now());
        return toVO(repository.save(selected));
    }

    @Transactional
    public StorageLocationVO test(Long id) {
        StorageLocation location = get(id);
        try {
            verify(location);
            location.setStatus("online");
            location.setLastError(null);
        } catch (RuntimeException e) {
            location.setStatus("error");
            location.setLastError(shortMessage(e));
        }
        location.setUpdatedAt(LocalDateTime.now());
        return toVO(repository.save(location));
    }

    @Transactional
    public StorageLocationVO scan(Long id) {
        StorageLocation location = get(id);
        try {
            Map<String, Long> stats = scanLocation(location);
            location.setAssetCount(stats.getOrDefault("assetCount", 0L));
            location.setUsedBytes(stats.getOrDefault("usedBytes", 0L));
            location.setStatus("online");
            location.setLastError(null);
        } catch (RuntimeException e) {
            location.setStatus("error");
            location.setLastError(shortMessage(e));
        }
        location.setLastScanAt(LocalDateTime.now());
        location.setUpdatedAt(LocalDateTime.now());
        return toVO(repository.save(location));
    }

    @Transactional
    public void delete(Long id) {
        StorageLocation location = get(id);
        if (Boolean.TRUE.equals(location.getProtectedLocation())) throw new BusinessException("平台默认存储位置不能删除");
        if (Boolean.TRUE.equals(location.getCurrent())) throw new BusinessException("当前存储位置不能删除，请先切换到其他位置");
        repository.delete(location);
    }

    private Map<String, Long> scanLocation(StorageLocation location) {
        if ("MINIO".equals(location.getType())) return storageService.scanStatistics();
        String scanPath = "LOCAL".equals(location.getType()) ? location.getAddress() : location.getMountPath();
        if (scanPath != null && !scanPath.isBlank()) return scanDirectory(scanPath);
        verifyRemote(location);
        return Map.of("assetCount", 0L, "usedBytes", 0L);
    }

    private Map<String, Long> scanDirectory(String value) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new BusinessException("目录不存在或服务器无访问权限: " + path);
        AtomicLong count = new AtomicLong();
        AtomicLong size = new AtomicLong();
        try (Stream<Path> stream = Files.walk(path)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                count.incrementAndGet();
                try { size.addAndGet(Files.size(file)); } catch (IOException ignored) { }
            });
        } catch (IOException e) {
            throw new BusinessException("扫描目录失败: " + e.getMessage());
        }
        return Map.of("assetCount", count.get(), "usedBytes", size.get());
    }

    private void verify(StorageLocation location) {
        if ("MINIO".equals(location.getType())) storageService.scanStatistics();
        else if ("LOCAL".equals(location.getType())) scanDirectory(location.getAddress());
        else if (location.getMountPath() != null && !location.getMountPath().isBlank()) scanDirectory(location.getMountPath());
        else verifyRemote(location);
    }

    private void verifyRemote(StorageLocation location) {
        HostPort target = parseHostPort(location.getAddress(), location.getType());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), 3000);
        } catch (IOException e) {
            throw new BusinessException("无法连接 " + target.host() + ":" + target.port() + "，请检查地址、网络和服务状态");
        }
    }

    private HostPort parseHostPort(String address, String type) {
        try {
            String normalized = address.startsWith("//") ? "smb:" + address : address;
            URI uri = URI.create(normalized.contains("://") ? normalized : type.toLowerCase(Locale.ROOT) + "://" + normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) throw new IllegalArgumentException();
            int defaultPort = "FTP".equals(type) ? 21 : "SFTP".equals(type) ? 22 : 445;
            return new HostPort(host, uri.getPort() > 0 ? uri.getPort() : defaultPort);
        } catch (Exception e) {
            throw new BusinessException("远程地址格式不正确");
        }
    }

    private StorageLocation get(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("存储位置不存在"));
    }

    private StorageLocationVO toVO(StorageLocation location) {
        return StorageLocationVO.builder().id(location.getId()).name(location.getName()).type(location.getType())
                .address(location.getAddress()).mountPath(location.getMountPath()).username(location.getUsername())
                .hasCredential(location.getCredentialSecret() != null && !location.getCredentialSecret().isBlank())
                .status(location.getStatus()).current(location.getCurrent()).protectedLocation(location.getProtectedLocation())
                .assetCount(location.getAssetCount()).usedBytes(location.getUsedBytes()).lastScanAt(location.getLastScanAt())
                .lastError(location.getLastError()).createdAt(location.getCreatedAt()).updatedAt(location.getUpdatedAt()).build();
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String shortMessage(RuntimeException e) {
        String message = e.getMessage() == null ? "连接失败" : e.getMessage();
        return message.length() > 780 ? message.substring(0, 780) : message;
    }
    private record HostPort(String host, int port) { }
}
