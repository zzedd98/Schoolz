package ma.schoolz.erp.inventory.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.inventory.application.InventoryService;
import ma.schoolz.erp.inventory.application.InventoryService.AssetCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssetStatusCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssetView;
import ma.schoolz.erp.inventory.application.InventoryService.AssignmentCommand;
import ma.schoolz.erp.inventory.application.InventoryService.AssignmentView;
import ma.schoolz.erp.inventory.application.InventoryService.Dashboard;
import ma.schoolz.erp.inventory.application.InventoryService.ItemCommand;
import ma.schoolz.erp.inventory.application.InventoryService.ItemView;
import ma.schoolz.erp.inventory.application.InventoryService.LocationCommand;
import ma.schoolz.erp.inventory.application.InventoryService.LocationView;
import ma.schoolz.erp.inventory.application.InventoryService.MovementCommand;
import ma.schoolz.erp.inventory.application.InventoryService.MovementView;
import ma.schoolz.erp.inventory.application.InventoryService.ReturnCommand;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return inventory.dashboard(actor);
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationView createLocation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody LocationCommand command,
            HttpServletRequest request) {
        return inventory.createLocation(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView createItem(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ItemCommand command,
            HttpServletRequest request) {
        return inventory.createItem(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/items/{itemId}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementView recordMovement(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID itemId,
            @RequestBody MovementCommand command,
            HttpServletRequest request) {
        return inventory.recordMovement(actor, itemId, command, request.getRemoteAddr());
    }

    @PostMapping("/assets")
    @ResponseStatus(HttpStatus.CREATED)
    public AssetView createAsset(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AssetCommand command,
            HttpServletRequest request) {
        return inventory.createAsset(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/assets/{assetId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assignAsset(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID assetId,
            @RequestBody AssignmentCommand command,
            HttpServletRequest request) {
        return inventory.assignAsset(actor, assetId, command, request.getRemoteAddr());
    }

    @PostMapping("/assignments/{assignmentId}/return")
    public AssignmentView returnAsset(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID assignmentId,
            @RequestBody ReturnCommand command,
            HttpServletRequest request) {
        return inventory.returnAsset(actor, assignmentId, command, request.getRemoteAddr());
    }

    @PutMapping("/assets/{assetId}/status")
    public AssetView changeAssetStatus(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID assetId,
            @RequestBody AssetStatusCommand command,
            HttpServletRequest request) {
        return inventory.changeAssetStatus(actor, assetId, command, request.getRemoteAddr());
    }
}
