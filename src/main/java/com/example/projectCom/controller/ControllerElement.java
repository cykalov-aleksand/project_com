package com.example.projectCom.controller;

import com.example.projectCom.dto.EquipmentObjectDto;
import com.example.projectCom.service.PanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;



@Controller
@RequiredArgsConstructor
public class ControllerElement {

    private final PanelService panelService;

    @GetMapping("/")
    public String find(Model model) {
       List<EquipmentObjectDto> element=panelService.findAllObject();
       // model.addAttribute("listElement", element);
        model.addAttribute("listElement", (element != null) ? element : "");
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
        System.out.println(dto);
        panelService.saveObjectDto(dto);
        return "redirect:/";
    }
    @PostMapping("/delete/{id}")
    public String deleteCell(@PathVariable Long id) {
        panelService.delete(id); // или service.delete(id)
        return "redirect:/"; // после удаления сразу на список
    }
}