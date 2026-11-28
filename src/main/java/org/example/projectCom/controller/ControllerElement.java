package org.example.projectCom.controller;

import jakarta.persistence.EntityNotFoundException;
import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.dto.PanelTestResult;
import org.example.projectCom.port.SerialPortService;
import org.example.projectCom.service.AgentTokenService;
import org.example.projectCom.service.ObjectService;
import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.example.projectCom.service.PanelComService;
import org.example.projectCom.service.errors.SerialPortException;
import org.example.projectCom.service.panelService.AgentStatusService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ControllerElement {
    private final SerialPortService serialPortService;
    private final ObjectService panelService;
    private final PanelComService panelComService;
    private final AgentTokenService agentTokenService;
    private final AgentStatusService agentStatusService;
    @PostMapping("/agent/token")
    public String issueAgentToken(Principal principal, RedirectAttributes ra) {
        String token = agentTokenService.issue(principal.getName());
        ra.addFlashAttribute("agentToken", token);
        ra.addFlashAttribute("portMessage", "Токен агента получен. Скопируйте его в приложение агента.");
        return "redirect:/";
    }

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
    public String find(@RequestParam(required = false) String search, Model model,Principal principal) {
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
        if (principal != null) {
            AgentStatusService.AgentStatus status = agentStatusService.getStatus(principal.getName());
            System.err.println(status.toString());
            model.addAttribute("agentStatus", status);
            model.addAttribute("currentUser", principal.getName());
        } else {
            model.addAttribute("agentStatus", new AgentStatusService.AgentStatus(false, null));
        }
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
    public String sendAllPanelsForObject(@PathVariable Long id, RedirectAttributes ra) {
        try {
            String result = panelComService.sendObjectPanels(id);
            ra.addFlashAttribute("testResults", Map.of(id, new PanelTestResult(result.startsWith("Передано"), result)));
        } catch (EntityNotFoundException | IllegalStateException e) {
            ra.addFlashAttribute("testResults",
                    Map.of(id, PanelTestResult.fail("Ошибка: " + e.getMessage())));
        }
        return "redirect:/"; // или на нужную страницу
    }
}