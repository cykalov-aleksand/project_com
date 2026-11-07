package com.example.projectCom.controller;


import com.example.projectCom.dto.PanelDto;
import com.example.projectCom.model.EquipmentObject;
import com.example.projectCom.model.Panels;
import com.example.projectCom.service.ObjectService;
import com.example.projectCom.service.OutputUtils;
import com.example.projectCom.service.PanelsService;
import com.example.projectCom.setup_controller.OutputOption;
import com.example.projectCom.setup_controller.PanelNameOption;
import com.example.projectCom.setup_controller.SyncPeriodOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;

import static com.example.projectCom.setup_controller.PanelNameOption.PANEL_OUTPUT_SWITCHING;
import static com.example.projectCom.setup_controller.PanelNameOption.PANEL_SYNCHRONIZATION;

@Controller
@RequiredArgsConstructor
public class ControllerPanels {
    private final PanelsService panelsService;
    private final ObjectService objectService;

    @GetMapping("/panels/edit/{id}/{name}")
    public String showEditPanelsNameObject(@PathVariable Long id, @PathVariable String name,
                                           Model model) {
        List<PanelDto> panels = panelsService.findPanelsIdObject(id);
        model.addAttribute("availableNames", PanelNameOption.values());
        model.addAttribute("panels", panels);
        model.addAttribute("objectName", name);
        model.addAttribute("objectId", id);
        return "edit-panels";
    }

    @GetMapping("/panels/editPanel/{panelId}")
    public String showEditSinglePanel(@PathVariable Long panelId, Model model) {
        Panels panel = panelsService.getPanelById(panelId);
        model.addAttribute("newPanel", panel);
        model.addAttribute("objectId", panel.getObject().getId());
        model.addAttribute("availableNames", panel.getNameElement());
        model.addAttribute("pageTitle", "Редактирование панели с ID=" + panelId);
        return "create-panel";
    }

    @PostMapping("/panels/save")
    public String saveObject(@ModelAttribute("panelDto") PanelDto panelDto,
                             @RequestParam Long objectId) {
        PanelDto dto = panelsService.savePanel(panelDto, objectId);
        String objectName = objectService.getNameById(objectId);
        String encodedName = UriUtils.encodePath(objectName, StandardCharsets.UTF_8);
        return "redirect:/panels/" + dto.getId();
    }

    @GetMapping("/panels/create")
    public String showCreatePanel(@RequestParam Long objectId, Model model) {
        // Панель выходов — обязательная основа. Нет её — настраиваем сначала её.
        if (!panelsService.existsPanelType(objectId, PANEL_OUTPUT_SWITCHING)) {
            return "redirect:/panels/" + panelsService.createPanel(objectId, PANEL_OUTPUT_SWITCHING).getId();
        }
        if (!panelsService.existsPanelType(objectId, PANEL_SYNCHRONIZATION)) {
            return "redirect:/panels/" + panelsService.createPanel(objectId, PANEL_SYNCHRONIZATION).getId();
        }
        List<PanelNameOption> available = panelsService.getAvailablePanelTypes(objectId);
        model.addAttribute("newPanel", new Panels());
        model.addAttribute("objectId", objectId);
        model.addAttribute("availableNames", available);
        return "create-panel";
    }

    @PostMapping("/panels/delete/{id}")
    public String deleteCell(@PathVariable Long id) {
        Panels panel = panelsService.getPanelById(id);
        Long objectId = panel.getObject().getId();
        String objectName = panel.getObject().getName();
        panelsService.delete(id);
        String encodedName = UriUtils.encodePath(objectName, StandardCharsets.UTF_8);
        return "redirect:/panels/edit/" + objectId + "/" + encodedName;
    }

    @PostMapping("/panels/json/output/save")
    public String saveOutput(@ModelAttribute("newPanel") Panels panel,
                             @RequestParam Long objectId,
                             @RequestParam(required = false) List<String> outputs,
                             RedirectAttributes redirectAttributes) {
        EquipmentObject equipmentObject = objectService.getById(objectId);
        if (equipmentObject == null) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Объект с ID=" + objectId + " не найден");
            return "redirect:/panels";
        }
        // Превращаем имена чекбоксов в Enum и упаковываем в байты
        List<OutputOption> selected = (outputs == null) ? List.of()
                : outputs.stream()
                .map(OutputOption::valueOf)
                .toList();

        byte[] packed = OutputUtils.packOutputs(selected, 5);
        // 3. Сохраняем конфигурацию, передавая саму сущность объекта
        panelsService.saveOutputConfig(panel.getId(), equipmentObject, packed);
        redirectAttributes.addFlashAttribute("successMessage",
                "Шаг настройки выходов пройден");
        // Возврат к списку панелей ячейки
        String objectName = objectService.getNameById(objectId);
        String encodedName = UriUtils.encodePath(objectName, StandardCharsets.UTF_8);
        return "redirect:/panels/edit/" + objectId + "/" + encodedName;
    }

    @GetMapping("/panels/{panelId}")
    public String showConfigureOutputs(@PathVariable Long panelId, Model model) {
        Panels panel = panelsService.getPanelById(panelId);
        model.addAttribute("newPanel", panel);
        model.addAttribute("objectId", panel.getObject().getId());
        model.addAttribute("pageTitle", "Настройка выходов панели c ID=" + panelId);
        model.addAttribute("panelName", panel.getNameElement().getDisplayName());
        model.addAttribute("outputGroups",
                panelsService.groupOfPointersWeekends(panel.getNameElement(), panel.getObject().getId()));

        // Отмеченные выходы для предотмеченных чекбоксов (th:checked)
        model.addAttribute("selectedOutputs", new LinkedHashSet<>(
                OutputUtils.extractSelectedOutputs(panel.getOutputsJsonString())));

        if (panel.getNameElement() == PANEL_OUTPUT_SWITCHING) {
            return "edit-window-output-panel";
        }
        if (panel.getNameElement() == PANEL_SYNCHRONIZATION) {
            model.addAttribute("syncPeriods", SyncPeriodOption.values());
            model.addAttribute("currentDivisor",
                    OutputUtils.extractDivisor(panel.getOutputsJsonString()));
            System.err.println(OutputUtils.extractDivisor(panel.getOutputsJsonString()));
            model.addAttribute("selectedOutputs", new LinkedHashSet<>(
                    OutputUtils.extractSelectedOutputs(panel.getOutputsJsonString(), 3)));
            return "edit-window-synhronization-panel";
        }
        return "edit-window-generation-panel";
    }
    @PostMapping("/panels/json/sync/save")
    public String saveSync(@RequestParam Long panelId,
                           @RequestParam Long objectId,
                           @RequestParam Integer period,
                           @RequestParam(required = false) List<String> outputs,
                           RedirectAttributes redirectAttributes) {
        List<OutputOption> selected = (outputs == null) ? List.of()
                : outputs.stream().map(OutputOption::valueOf).toList();

        panelsService.saveSyncConfig(panelId, objectId, period, selected);
        redirectAttributes.addFlashAttribute("successMessage", "Панель синхронизации настроена");
        String objectName = objectService.getNameById(objectId);
        String encodedName = UriUtils.encodePath(objectName, StandardCharsets.UTF_8);
        return "redirect:/panels/edit/" + objectId + "/" + encodedName;
    }
}
