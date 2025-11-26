package shortly.mandmcorp.dev.shortly.security;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import shortly.mandmcorp.dev.shortly.repository.UserRepository;

@Service
@AllArgsConstructor
@Getter
public class UserDetailService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String phoneNumber) throws UsernameNotFoundException {
        return userRepository.findByPhoneNumber(phoneNumber);
    } 
}
