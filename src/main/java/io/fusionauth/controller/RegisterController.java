package io.fusionauth.controller;

import java.util.Objects;
import java.util.UUID;

import com.inversoft.rest.ClientResponse;
import io.fusionauth.client.FusionAuthClient;
import io.fusionauth.domain.User;
import io.fusionauth.domain.UserRegistration;
import io.fusionauth.domain.api.user.RegistrationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.view.RedirectView;

/**
 * @author Tyler Scott
 */
@Controller
public class RegisterController {
  private final FusionAuthClient fusionAuthClient;

  @Value("${fusionAuth.applicationId}")
  private String appId;

  public RegisterController(FusionAuthClient fusionAuthClient) {
    this.fusionAuthClient = fusionAuthClient;
  }

  @RequestMapping(value = "/register", method = RequestMethod.POST,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public View handleRegister(@RequestBody MultiValueMap<String, String> body, OAuth2AuthenticationToken authentication) {
    ClientResponse response;

    if (authentication.isAuthenticated()) { // User is logged in
      UserRegistration registration = new UserRegistration()
          .with(reg -> reg.userId = UUID.fromString(authentication.getName()))
          .with(reg -> reg.applicationId = UUID.fromString(appId))
          .with(reg -> reg.roles.add("user"));

      response = fusionAuthClient.register(registration.userId, new RegistrationRequest(null, registration));
    } else { // This is a new user
      String email = body.getFirst("email");
      String password = body.getFirst("password");
      String confirmPassword = body.getFirst("confirmPassword");
      validateInput(email, password, confirmPassword);

      UserRegistration registration = new UserRegistration()
          .with(reg -> reg.applicationId = UUID.fromString(appId))
          .with(reg -> reg.roles.add("user"));

      User newUser = new User()
          .with(user -> user.email = body.getFirst("email"))
          .with(user -> user.password = body.getFirst("password"));

      response = fusionAuthClient.register(null, new RegistrationRequest(newUser, registration));
    }

    if (response.wasSuccessful()) {
      // Force the user to logout and then login again (we didn't log them out of fusionauth so it should be seamless to them)
      // We do this because we need to refresh their roles.
      return new RedirectView("/logout?redirect=/login");
    } else {
      throw new RegistrationException(response.errorResponse.toString());
    }
  }

  @RequestMapping(value = "/register", method = RequestMethod.GET)
  public String viewRegister() {
    return "register";
  }

  private void validateInput(String email, String password, String confirmPassword) {
    if (email != null && email.length() == 0) {
      throw new RegistrationException("Email is required.");
    }

    if (password != null && password.length() == 0 || confirmPassword != null && confirmPassword.length() == 0) {
      throw new RegistrationException("Password and Confirm Password is required.");
    }

    if (!Objects.equals(password, confirmPassword)) {
      throw new RegistrationException("Passwords do not match.");
    }
  }

  public class RegistrationException extends RuntimeException {
    RegistrationException(String cause) {
      super(cause);
    }
  }
}
