
package shortly.mandmcorp.dev.shortly.config.security;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception   {
        http.csrf(customizer -> customizer.disable());
        http.authorizeHttpRequests(
            request -> request.requestMatchers("/user/**").permitAll()
            .requestMatchers("/health/**").permitAll()
            .anyRequest().authenticated() );
        return http.build();
    }


}