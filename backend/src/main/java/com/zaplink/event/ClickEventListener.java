package com.zaplink.event;

import com.zaplink.model.Click;
import com.zaplink.repository.ClickRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ClickEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClickEventListener.class);

    private final ClickRepository clickRepository;

    public ClickEventListener(ClickRepository clickRepository) {
        this.clickRepository = clickRepository;
    }

    @Async("clickLoggerExecutor")
    @EventListener
    public void handleClickRecorded(ClickRecordedEvent event) {
        try {
            Click click = new Click();
            click.setLinkId(event.linkId());
            click.setClickedAt(event.clickedAt());
            clickRepository.save(click);
        } catch (Exception ex) {
            log.warn("Failed to persist click for linkId={}: {}", event.linkId(), ex.getMessage());
        }
    }
}
