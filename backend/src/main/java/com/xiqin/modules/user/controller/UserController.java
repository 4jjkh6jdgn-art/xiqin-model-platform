package com.xiqin.modules.user.controller;

import com.xiqin.common.result.PageResult;
import com.xiqin.common.result.Result;
import com.xiqin.modules.auth.dto.UpdateUserRequest;
import com.xiqin.modules.auth.dto.UserVO;
import com.xiqin.modules.user.service.UserService;
import com.xiqin.modules.user.dto.BatchCreateUserRequest;
import com.xiqin.modules.user.dto.CreateUserRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:create')")
    public Result<UserVO> createUser(@Valid @RequestBody CreateUserRequest req) {
        return Result.success(userService.createUser(req));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:batch_create')")
    public Result<List<UserVO>> batchCreateUsers(@Valid @RequestBody BatchCreateUserRequest req) {
        return Result.success(userService.batchCreateUsers(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:view')")
    public Result<PageResult<UserVO>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(PageResult.of(userService.searchUsers(keyword, status, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:view')")
    public Result<UserVO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:edit')")
    public Result<UserVO> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return Result.success(userService.updateUser(id, req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:status')")
    public Result<UserVO> updateStatus(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        UpdateUserRequest statusOnly = new UpdateUserRequest();
        statusOnly.setStatus(request.getStatus());
        return Result.success(userService.updateUser(id, statusOnly));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('user:delete')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('profile:edit')")
    public Result<UserVO> updateProfile(HttpServletRequest request, @RequestBody UpdateUserRequest req) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.updateProfile(userId, req));
    }

    @GetMapping("/me")
    public Result<UserVO> currentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getUserById(userId));
    }
}
