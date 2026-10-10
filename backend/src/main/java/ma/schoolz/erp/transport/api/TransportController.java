package ma.schoolz.erp.transport.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.transport.application.TransportService;
import ma.schoolz.erp.transport.application.TransportService.AssignmentCommand;
import ma.schoolz.erp.transport.application.TransportService.AssignmentView;
import ma.schoolz.erp.transport.application.TransportService.AttendanceCommand;
import ma.schoolz.erp.transport.application.TransportService.PositionCommand;
import ma.schoolz.erp.transport.application.TransportService.RouteCommand;
import ma.schoolz.erp.transport.application.TransportService.RouteView;
import ma.schoolz.erp.transport.application.TransportService.StartTripCommand;
import ma.schoolz.erp.transport.application.TransportService.SubscriptionCommand;
import ma.schoolz.erp.transport.application.TransportService.SubscriptionView;
import ma.schoolz.erp.transport.application.TransportService.TransportDashboard;
import ma.schoolz.erp.transport.application.TransportService.TripView;
import ma.schoolz.erp.transport.application.TransportService.VehicleCommand;
import ma.schoolz.erp.transport.application.TransportService.VehicleView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transport")
public class TransportController {
    private final TransportService transport;

    public TransportController(TransportService transport) {
        this.transport = transport;
    }

    @GetMapping("/dashboard")
    public TransportDashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return transport.dashboard(actor);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleView vehicle(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody VehicleCommand command,
            HttpServletRequest request) {
        return transport.createVehicle(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteView route(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody RouteCommand command,
            HttpServletRequest request) {
        return transport.createRoute(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionView subscription(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SubscriptionCommand command,
            HttpServletRequest request) {
        return transport.subscribe(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assignment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AssignmentCommand command,
            HttpServletRequest request) {
        return transport.assign(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/trips")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView trip(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody StartTripCommand command,
            HttpServletRequest request) {
        return transport.startTrip(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/trips/{tripId}/pickups")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView pickup(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID tripId,
            @RequestBody AttendanceCommand command,
            HttpServletRequest request) {
        return transport.pickup(actor, tripId, command, request.getRemoteAddr());
    }

    @PostMapping("/trips/{tripId}/dropoffs")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView dropoff(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID tripId,
            @RequestBody AttendanceCommand command,
            HttpServletRequest request) {
        return transport.dropoff(actor, tripId, command, request.getRemoteAddr());
    }

    @PostMapping("/trips/{tripId}/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView position(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID tripId,
            @RequestBody PositionCommand command,
            HttpServletRequest request) {
        return transport.position(actor, tripId, command, request.getRemoteAddr());
    }
}
