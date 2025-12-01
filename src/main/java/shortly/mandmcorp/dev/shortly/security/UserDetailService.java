package shortly.mandmcorp.dev.shortly.security;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import shortly.mandmcorp.dev.shortly.repository.UserRepository;

/**
 * Custom UserDetailsService implementation for Spring Security authentication.
 * Loads user details from the database using phone number as the username.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
@Service
@AllArgsConstructor
@Getter
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads user details by phone number for Spring Security authentication.
     * 
     * @param phoneNumber the phone number used as username (must be in +233XXXXXXXXX format)
     * @return UserDetails object containing user authentication information
     * @throws UsernameNotFoundException if no user is found with the given phone number
     */
    @Override
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        return userRepository.findByPhoneNumber(phoneNumber);
    } 
}
