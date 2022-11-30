package com.example.CalenderProject.controller;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.CalenderProject.dto.EventDTO;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

@RestController
public class CalenderController {
	private static final String APPLICATION_NAME = "Spring";
	private static HttpTransport httpTransport;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static com.google.api.services.calendar.Calendar client;

	GoogleClientSecrets clientSecrets;
	GoogleAuthorizationCodeFlow flow;
	Credential credential;

	@Value("${google.client.client-id}")
	private String clientId;
	@Value("${google.client.client-secret}")
	private String clientSecret;
	@Value("${google.client.redirectUri}")
	private String redirectURI;

	private Map<String, Set<Event>> events = new HashMap<>();

	public void setEvent(Event event) {
		Set<Event> events = new HashSet<>();
		events.add(event);
		this.events.put(clientSecret, events);
	}

	@PostMapping(path = "/login")
	public ResponseEntity<String> googleConnectionStatus(@RequestBody EventDTO eventDTO) throws Exception {
		Event event = createEvent(eventDTO);
		this.setEvent(event);

		String status = authorize();
		return new ResponseEntity<>(status, HttpStatus.OK);
	}

	private Event createEvent(EventDTO eventDTO) {
		Event event = new Event();
		event.setSummary(eventDTO.getSummary());
		event.setLocation(eventDTO.getLocation());
		event.setDescription(eventDTO.getDescription());

		DateTime startDateTime = new DateTime(eventDTO.getStartDateTime());
		EventDateTime start = new EventDateTime();
		start.setDateTime(startDateTime);
		start.setTimeZone(eventDTO.getLocation());
		event.setStart(start);

		DateTime endDateTime = new DateTime(eventDTO.getEndDateTime());

		EventDateTime end = new EventDateTime();
		end.setDateTime(endDateTime);
		end.setTimeZone(eventDTO.getLocation());
		event.setEnd(end);

		return event;
	}

	@GetMapping(path = "/login/google")
	public ResponseEntity<String> oauth2Callback(@RequestParam(value = "code") String code) {
		String message;
		try {
			TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectURI).execute();
			credential = flow.createAndStoreCredential(response, "userID");
			client = new com.google.api.services.calendar.Calendar.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build();
			String calendarId = "primary";

			Iterator<Event> eventsItr = getEvents().iterator();
			while (eventsItr.hasNext()) {
				Event event = client.events().insert(calendarId, eventsItr.next()).execute();
			}
			message = "Event created successfully";
		} catch (Exception e) {
			message = "Exception while handling OAuth2 callback (" + e.getMessage() + ")."
					+ " Redirecting to google connection status page.";
		}
		return new ResponseEntity<>(message, HttpStatus.OK);
	}

	public Set<Event> getEvents() throws IOException {
		return this.events.get(clientSecret);
	}

	private String authorize() throws Exception {
		AuthorizationCodeRequestUrl authorizationUrl;
		if (flow == null) {
			Details web = new Details();
			web.setClientId(clientId);
			web.setClientSecret(clientSecret);
			clientSecrets = new GoogleClientSecrets().setWeb(web);
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
					Collections.singleton(CalendarScopes.CALENDAR)).build();
		}
		authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectURI);
		return authorizationUrl.build();

	}
}
