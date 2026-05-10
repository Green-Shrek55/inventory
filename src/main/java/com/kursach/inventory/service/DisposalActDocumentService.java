package com.kursach.inventory.service;

import com.kursach.inventory.domain.DisposalScan;
import com.kursach.inventory.domain.DisposalSession;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DisposalActDocumentService {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DisposalSessionService disposalSessionService;

    public DisposalActDocumentService(DisposalSessionService disposalSessionService) {
        this.disposalSessionService = disposalSessionService;
    }

    @Transactional(readOnly = true)
    public byte[] buildAct(Long sessionId) {
        DisposalSession session = disposalSessionService.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Сессия утилизации не найдена"));
        List<DisposalScan> scans = disposalSessionService.findScans(sessionId);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, session);
            addDetails(document, session);
            addItemsTable(document, scans);
            addSignatures(document);
            document.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сформировать акт утилизации", ex);
        }
    }

    public String filename(Long sessionId) {
        return "disposal-act-" + sessionId + ".docx";
    }

    private void addTitle(XWPFDocument document, DisposalSession session) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = title.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText("Акт утилизации компьютерной техники № " + session.getId());
    }

    private void addDetails(XWPFDocument document, DisposalSession session) {
        addText(document, "Организация: ФГБОУ ВО «Российский экономический университет имени Г.В. Плеханова»");
        addText(document, "Корпус: " + (session.getBuilding() == null ? "-" : session.getBuilding().getName()));
        addText(document, "Начало сессии: " + format(session.getStartedAt()));
        addText(document, "Завершение сессии: " + format(session.getFinishedAt()));
        addText(document, "Номер пломбы: " + emptyToDash(session.getSealNumber()));
        addText(document, "Настоящий акт подтверждает передачу перечисленного оборудования на утилизацию.");
        document.createParagraph();
    }

    private void addItemsTable(XWPFDocument document, List<DisposalScan> scans) {
        XWPFTable table = document.createTable(scans.size() + 1, 6);
        table.setWidth("100%");
        table.setWidthType(TableWidthType.PCT);
        XWPFTableRow header = table.getRow(0);
        setCell(header.getCell(0), "№");
        setCell(header.getCell(1), "Инв. номер / ШК");
        setCell(header.getCell(2), "Наименование");
        setCell(header.getCell(3), "Тип");
        setCell(header.getCell(4), "Место учета");
        setCell(header.getCell(5), "Дата скана");

        for (int i = 0; i < scans.size(); i++) {
            DisposalScan scan = scans.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCell(row.getCell(0), String.valueOf(i + 1));
            setCell(row.getCell(1), scan.getInventoryNumber());
            setCell(row.getCell(2), scan.getEquipmentName());
            setCell(row.getCell(3), scan.getTypeName());
            setCell(row.getCell(4), scan.getLocationName());
            setCell(row.getCell(5), format(scan.getScannedAt()));
        }
        document.createParagraph();
    }

    private void addSignatures(XWPFDocument document) {
        addText(document, "Утилизирующее лицо: ________________________________");
        addText(document, "ФИО: ________________________________");
        addText(document, "Подпись: ________________________________");
        addText(document, "Дата: «____» __________________ 20____ г.");
    }

    private void addText(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(11);
        run.setText(text);
    }

    private void setCell(XWPFTableCell cell, String text) {
        cell.removeParagraph(0);
        XWPFParagraph paragraph = cell.addParagraph();
        XWPFRun run = paragraph.createRun();
        run.setFontSize(9);
        run.setText(text == null ? "" : text);
    }

    private String format(java.time.Instant instant) {
        return instant == null ? "-" : DATE_TIME.format(instant.atZone(MOSCOW));
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
