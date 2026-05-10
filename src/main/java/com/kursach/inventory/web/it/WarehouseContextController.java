package com.kursach.inventory.web.it;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/warehouse/context")
public class WarehouseContextController {
    public static final String BUILDING_ID_SESSION_KEY = "WAREHOUSE_BUILDING_ID";
    public static final String PLACEMENT_LOCATION_ID_SESSION_KEY = "WAREHOUSE_PLACEMENT_LOCATION_ID";
    public static final String BULK_PLACEMENT_ENABLED_SESSION_KEY = "WAREHOUSE_BULK_PLACEMENT_ENABLED";

    @PostMapping("/building")
    public String setBuilding(@RequestParam(required = false) Long buildingId,
                              HttpSession session,
                              HttpServletRequest request) {
        if (buildingId == null) {
            session.removeAttribute(BUILDING_ID_SESSION_KEY);
        } else {
            session.setAttribute(BUILDING_ID_SESSION_KEY, buildingId);
        }
        session.removeAttribute(PLACEMENT_LOCATION_ID_SESSION_KEY);
        session.removeAttribute(BULK_PLACEMENT_ENABLED_SESSION_KEY);
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer == null || referer.isBlank() ? "/warehouse/equipment" : referer);
    }

    @PostMapping("/placement-location")
    public String setPlacementLocation(@RequestParam(required = false) Long locationId,
                                       @RequestParam(defaultValue = "false") boolean enabled,
                                       HttpSession session,
                                       HttpServletRequest request) {
        if (!enabled || locationId == null) {
            session.removeAttribute(PLACEMENT_LOCATION_ID_SESSION_KEY);
            session.setAttribute(BULK_PLACEMENT_ENABLED_SESSION_KEY, false);
        } else {
            session.setAttribute(PLACEMENT_LOCATION_ID_SESSION_KEY, locationId);
            session.setAttribute(BULK_PLACEMENT_ENABLED_SESSION_KEY, true);
        }
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer == null || referer.isBlank() ? "/warehouse/equipment#placement" : referer);
    }
}
