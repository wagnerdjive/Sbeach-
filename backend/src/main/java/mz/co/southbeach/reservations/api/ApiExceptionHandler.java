package mz.co.southbeach.reservations.api;

import mz.co.southbeach.reservations.api.dto.ApiErrorResponse;
import mz.co.southbeach.reservations.service.ReservationCapacityExceededException;
import mz.co.southbeach.reservations.service.ReservationDateInPastException;
import mz.co.southbeach.reservations.service.ReservationVenueRequiredException;
import mz.co.southbeach.reservations.service.ReservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return error(HttpStatus.BAD_REQUEST, "Check the submitted fields.", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "The request body is invalid.", Map.of());
    }

    @ExceptionHandler(ReservationDateInPastException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handlePastDate(ReservationDateInPastException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), Map.of("requestedDate", exception.getMessage()));
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleNotFound(ReservationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler({ReservationCapacityExceededException.class, ReservationVenueRequiredException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleCapacity(RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleConflict(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), Map.of());
    }

    private ApiErrorResponse error(HttpStatus status, String message, Map<String, String> fields) {
        return new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, fields);
    }
}
