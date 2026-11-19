package org.example.projectCom.controller;


import org.example.projectCom.service.panelService.cacheServicePanel.PanelServiceCache;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.model.Panels;
import org.example.projectCom.service.*;
import org.example.projectCom.service.errors.NoFreeOutputsException;
import org.example.projectCom.service.errors.PanelValidationException;
import org.example.projectCom.service.panelService.PanelGeneration;
import org.example.projectCom.service.panelService.PanelOutputSwitch;
import org.example.projectCom.service.panelService.PanelSynchronisation;
import org.example.projectCom.setup_controller.OutputOption;
import org.example.projectCom.setup_controller.PanelNameOption;
import org.example.projectCom.setup_controller.SyncPeriodOption;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static org.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Controller
@RequiredArgsConstructor
public class ControllerPanels {
    // private final PanelsService panelsService;
    private final PanelServiceCache cachePanelService;
    private final ObjectService objectService;
    private final PanelGeneration panelGenerationIml;
    private final PanelSynchronisation panelSynchronisationIml;
    private final PanelOutputSwitch panelOutputSwitchIml;

    @GetMapping("/panels/edit/{id}/{name}")
    public String showEditPanelsNameObject(@PathVariable Long id, @PathVariable String name,
                                           Model model) {
        List<PanelDto> panels=cachePanelService.findPanelsIdObject(id);
        //List<PanelDto> panels = panelsService.findPanelsIdObject(id);
        model.addAttribute("availableNames", PanelNameOption.values());
        model.addAttribute("panels", panels);
        model.addAttribute("objectName", name);
        model.addAttribute("objectId", id);
        return "edit-panels";
    }

    @PostMapping("/panels/save")
    public String savePanel(@RequestParam Long objectId,
                            @RequestParam PanelNameOption panelNameOption) {
        Panels panel = cachePanelService.createPanel(objectId, panelNameOption);
        return "redirect:/panels/" + panel.getId();
    }

    @GetMapping("/panels/create")
    public String showCreatePanel(@RequestParam Long objectId, Model model) {
        List<PanelNameOption> available = cachePanelService.getAvailablePanelTypes(objectId);
        model.addAttribute("newPanel", new Panels());
        model.addAttribute("objectId", objectId);
        model.addAttribute("availableNames", available);
        return "create-panel";
    }

    @PostMapping("/panels/delete/{id}")
    public String deleteCell(@PathVariable Long id,
                             RedirectAttributes redirectAttributes) {
        PanelDto panel;
        try{
            panel=cachePanelService.getPanelDtoById(id);
        } catch (EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Панель не найдена (возможно, уже удалена)");
            return "redirect:/";
        }
        Long objectId=panel.getObjectId();
        try{
            if (panel.getNameElement().isGeneratorType()) {
                objectId = panelGenerationIml.delete(id);
            } else if (panel.getNameElement() == PANEL_SYNCHRONIZATION) {
                objectId = panelSynchronisationIml.delete(id);
            } else {
                objectId = panelOutputSwitchIml.delete(id);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Панель удалена");
            return redirectToEdit(objectId);
        } catch (PanelValidationException | IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return redirectToEdit(objectId);
        }
    }

    @GetMapping("/panels/{panelId}")
    public String showConfigureOutputs(@PathVariable Long panelId, Model model) {
        PanelDto panel = cachePanelService.getPanelDtoById(panelId);
        model.addAttribute("newPanel", panel);
        model.addAttribute("objectId", panel.getObjectId());
        model.addAttribute("pageTitle", "Настройка выходов панели c ID=" + panelId);
        model.addAttribute("panelName", panel.getNameElement().getDisplayName());
        model.addAttribute("outputGroups",
                cachePanelService.groupOfPointersWeekends(panel.getNameElement(), panel.getObjectId(), panelId));
        model.addAttribute("takenByOthers", cachePanelService.takenByOthers(panelId));
        model.addAttribute("selectedOutputs", new LinkedHashSet<>(
                OutputUtils.extractSelectedOutputs(panel.getListCondition(),
                        OutputUtils.payloadOffset(panel.getNameElement()))));
        if (panel.getNameElement() == PANEL_OUTPUT_SWITCHING) {
            return "edit-window-output-panel";
        }
        if (panel.getNameElement() == PANEL_SYNCHRONIZATION) {
            model.addAttribute("syncPeriods", SyncPeriodOption.values());
            model.addAttribute("currentDivisor",
                    OutputUtils.extractDivisor(panel.getListCondition()));
            model.addAttribute("selectedOutputs", new LinkedHashSet<>(
                    OutputUtils.extractSelectedOutputs(
                            panel.getListCondition(),
                            OutputUtils.payloadOffset(panel.getNameElement()))));
            return "edit-window-synhronization-panel";
        }
        return "edit-window-generation-panel";
    }

    @PostMapping("/panels/json/output/save")
    public String saveOutput(@RequestParam Long panelId,
                             @RequestParam Long objectId,
                             @RequestParam(required = false) List<String> outputs,
                             RedirectAttributes redirectAttributes) {
        try {
            panelOutputSwitchIml.save(panelId, objectId, outputs);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Шаг настройки выходов пройден");
        } catch (PanelValidationException | IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return redirectToEdit(objectId);
        }
        return redirectToEdit(objectId);
    }

    @PostMapping("/panels/json/sync/save")
    public String saveSync(@RequestParam Long panelId,
                           @RequestParam Long objectId,
                           @RequestParam Integer period,
                           @RequestParam(required = false) List<String> outputs,
                           RedirectAttributes redirectAttributes) {
        try {
            panelSynchronisationIml.save(panelId, period, outputs);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Панель синхронизации настроена");
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return redirectToEdit(objectId);
        }
        return redirectToEdit(objectId);
    }

    @PostMapping("/panels/json/generation/save")
    public String saveGenerated(@RequestParam Long panelId,
                                @RequestParam Long objectId,
                                @RequestParam(required = false) List<String> outputs,
                                RedirectAttributes redirectAttributes) {
        try {
            panelGenerationIml.save(panelId, outputs);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Панель генератора настроена");
        } catch (NoFreeOutputsException | IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return redirectToEdit(objectId);
        }
        return redirectToEdit(objectId);
    }

    @GetMapping("/panels/{panelId}/edit")
    public String showEditPanel(@PathVariable Long panelId, Model model) {
        PanelDto panel = cachePanelService.getPanelDtoById(panelId);
        List<OutputOption> selected = OutputUtils.extractSelectedOutputs(
                panel.getListCondition(),OutputUtils.payloadOffset(panel.getNameElement()));
        model.addAttribute("pageTitle", "Редактирование панели: "
                + panel.getNameElement().getDisplayName() + " c ID=" + panelId);
        model.addAttribute("panel", panel);
        model.addAttribute("objectId", panel.getObjectId());
        model.addAttribute("outputGroups", cachePanelService.buildOutputGrid(panelId));
        model.addAttribute("selected", selected);
        model.addAttribute("takenByOthers", cachePanelService.takenByOthers(panelId));
        if (panel.getNameElement() == PANEL_SYNCHRONIZATION) {
            model.addAttribute("currentDivisor",
                    OutputUtils.extractDivisor(panel.getListCondition()));
        }
        return "editing-panel";
    }

    private String redirectToEdit(Long objectId) {
        String objectName = objectService.getNameById(objectId);
        String encodedName = UriUtils.encodePath(objectName, StandardCharsets.UTF_8);
        return "redirect:/panels/edit/" + objectId + "/" + encodedName;
    }
    @PostMapping("/panels/{panelId}/edit/save")
    public String savePanelEdit(@PathVariable Long panelId,
                                @RequestParam(required = false) Integer period,
                                @RequestParam(required = false) List<String> outputs,
                                RedirectAttributes redirectAttributes) {
        PanelDto panel = cachePanelService.getPanelDtoById(panelId);
        Long objectId = panel.getObjectId();
        try {
            switch (panel.getNameElement()) {
                case PANEL_OUTPUT_SWITCHING ->
                        panelOutputSwitchIml.save(panelId, objectId, outputs);
                case PANEL_SYNCHRONIZATION ->
                        panelSynchronisationIml.save(panelId, period, outputs);
                default ->
                        panelGenerationIml.save(panelId, outputs);
            }
            redirectAttributes.addFlashAttribute("successMessage", "Настройки панели обновлены");
        } catch (PanelValidationException | IllegalArgumentException | EntityNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return redirectToEdit(objectId);
    }
}