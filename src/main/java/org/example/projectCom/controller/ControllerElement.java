package org.example.projectCom.controller;

import org.example.projectCom.dto.EquipmentObjectDto;
import org.example.projectCom.service.ObjectService;
import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ControllerElement {

    private final ObjectService panelService;

    @GetMapping("/")
    public String find(@RequestParam(required = false) String search, Model model) {
        SerialPort[] listComPort = SerialPort.getCommPorts();
        model.addAttribute("listComPort", listComPort);

        List<EquipmentObjectDto> element = (search == null || search.isBlank())
                ? panelService.findAllObject()
                : panelService.findByName(search);

        model.addAttribute("listElement", element);
        model.addAttribute("search", search);
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
}