package org.example.projectCom.controller;

import jakarta.persistence.EntityNotFoundException;
import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.dto.PanelDto;
import org.example.projectCom.dto.PanelTestResult;
import org.example.projectCom.service.ObjectService;
import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.example.projectCom.service.OutputUtils;
import org.example.projectCom.service.PanelTestService;
import org.example.projectCom.service.SerialPortService;
import org.example.projectCom.service.errors.SerialPortException;
import org.example.projectCom.service.panelService.cacheServicePanel.PanelServiceCache;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ControllerElement {
    private final SerialPortService serialPortService;
    private final ObjectService panelService;
    private final PanelServiceCache cachePanelService;
    private final PanelTestService panelTestService;
    @GetMapping("/com_port")
    public String choiceComPort(@RequestParam String nameComPort, RedirectAttributes ra) {
        try {
            serialPortService.open(nameComPort);
            ra.addFlashAttribute("portMessage", "Порт " + nameComPort + " открыт");
        } catch (SerialPortException e) {
            ra.addFlashAttribute("portError", e.getMessage());
        }
        return "redirect:/";
    }
    @GetMapping("/")
    public String find(@RequestParam(required = false) String search, Model model) {
        SerialPort[] listComPort;
        try {
            listComPort = SerialPort.getCommPorts();
        } catch (Exception e) {
            listComPort = new SerialPort[0];
        }
        model.addAttribute("listComPort", listComPort);

        List<EquipmentObjectDto> element = (search == null || search.isBlank())
                ? panelService.findAllObject()
                : panelService.findByName(search);

        model.addAttribute("listElement", element);
        model.addAttribute("search", search);
        model.addAttribute("currentPortName", serialPortService.getCurrentPortName());
        if (!model.containsAttribute("testResults")) {
            model.addAttribute("testResults", java.util.Map.of());
        }
        return "foundation";
    }

    // Форма редактирования
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        EquipmentObjectDto dto = panelService.findById(id);
        System.out.println(dto);
        model.addAttribute("objectDto", dto);
        return "create-object";
    }

    // Форма создания (обязательно этот путь!)
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("objectDto", new EquipmentObjectDto());
        return "create-object";
    }

    // Сохранение (и создание, и обновление)
    @PostMapping("/save")
    public String saveObject(@ModelAttribute("objectDto") EquipmentObjectDto dto) {
        panelService.saveObjectDto(dto);
        return "redirect:/";
    }
    @PostMapping("/delete/{id}")
    public String deleteCell(@PathVariable Long id) {
        panelService.delete(id); // или service.delete(id)
        return "redirect:/"; // после удаления сразу на список
    }
    @PostMapping("/objects/test/{id}")
    public String runElementTest(@PathVariable Long id, RedirectAttributes ra) {
        try {
            List<PanelDto> panels = cachePanelService.findPanelsIdObject(id);
            if (panels.isEmpty()) {
                ra.addFlashAttribute("testResults",
                        java.util.Map.of(id, PanelTestResult.fail("У ячейки нет панелей")));
                return "redirect:/";
            }

            int sent = 0;
            List<String> problems = new java.util.ArrayList<>();
            for (PanelDto panel : panels) {
                String name = panel.getNameElement().getDisplayName();
                if (panel.getListCondition() == null || panel.getListCondition().isBlank()) {
                    problems.add(name + ": не настроена");
                    continue;
                }
                try {
                    byte[] payload = OutputUtils.fromJsonbHex(panel.getListCondition());
                    PanelTestResult r = panelTestService.runTest(panel.getId(), payload);
                    if (r.ok()) {
                        sent++;
                    } else {
                        problems.add(name + ": " + r.text());
                    }
                } catch (IllegalArgumentException e) {
                    problems.add(name + ": " + e.getMessage());
                }
            }

            String text = problems.isEmpty()
                    ? "Отправлено " + sent + " из " + panels.size() + " панелей"
                    : "Отправлено " + sent + " из " + panels.size()
                    + ". Проблемы: " + String.join("; ", problems);

            ra.addFlashAttribute("testResults",
                    java.util.Map.of(id, new PanelTestResult(problems.isEmpty(), text)));
        } catch (EntityNotFoundException | IllegalStateException e) {
            ra.addFlashAttribute("testResults",
                    java.util.Map.of(id, PanelTestResult.fail("Ошибка: " + e.getMessage())));
        }
        return "redirect:/";
    }
}