package com.sekai.song.control;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class UploadExceptionControl {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSize(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "音频文件太大，当前最多支持 1GB。");
        return "redirect:/songs";
    }
}
