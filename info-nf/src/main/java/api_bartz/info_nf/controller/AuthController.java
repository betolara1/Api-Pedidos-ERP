package api_bartz.info_nf.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.betolara1.jwt_package.security.JwtUtil;


@RestController
@RequestMapping("/auth")
public class AuthController {

    private final String username;
    private final String password;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            @Value("${auth.admin.user}") String username,
            @Value("${auth.admin.password}") String password,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder) {
        this.username = username;
        this.password = password;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public ResponseEntity<String> loginGet(@RequestParam String username, @RequestParam String password) {
        if (this.username.equals(username) && passwordEncoder.matches(password, this.password)) {
            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(token);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário ou senha inválida!");
    }
}