package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.user.dto.UserCreateRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import com.att.tdp.issueflow.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for the User aggregate. Path semantics mirror {@code openapi.yaml}. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.findAll();
    }

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserCreateRequest body) {
        return userService.create(body);
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable Long userId) {
        return userService.findById(userId);
    }

    @PatchMapping("/update/{userId}")
    public UserResponse update(@PathVariable Long userId,
                               @Valid @RequestBody UserUpdateRequest body) {
        return userService.update(userId, body);
    }

    @DeleteMapping("/{userId}")
    public void delete(@PathVariable Long userId) {
        userService.delete(userId);
    }
}
