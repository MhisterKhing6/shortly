package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest;
import shortly.mandmcorp.dev.shortly.dto.response.UserRegistrationResponse;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RestController
@RequestMapping("/api-user")
@AllArgsConstructor

public class UserController {
    private UserServiceInterface userService;

    @PostMapping("/register")
    public UserRegistrationResponse registerUser(@RequestBody @Valid UserRegistrationRequest userRegistrationRequest) {
        return  userService.register(userRegistrationRequest);
    }

    @PostMapping("/login")
    public String userLogin(@RequestBody String entity) {
        
        return entity;
    }
    
    
    
}
