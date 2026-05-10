package com.kursach.inventory.service;

import com.kursach.inventory.domain.ReceiptRequest;
import com.kursach.inventory.domain.ReceiptRequestItem;
import com.kursach.inventory.domain.ReceiptRequestStatus;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReceiptActDocumentService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final ReceiptRequestService receiptRequestService;

    public ReceiptActDocumentService(ReceiptRequestService receiptRequestService) {
        this.receiptRequestService = receiptRequestService;
    }

    @Transactional(readOnly = true)
    public byte[] buildAct(Long requestId) {
        ReceiptRequest request = receiptRequestService.getById(requestId);
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTitle(document, request);
            addRequestDetails(document, request);
            addItemsTable(document, request, request.getItems());
            addSignatures(document, request);
            document.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сформировать акт приемки", ex);
        }
    }

    public String filename(Long requestId) {
        return "receipt-act-" + requestId + ".docx";
    }

    private void addTitle(XWPFDocument document, ReceiptRequest request) {
        XWPFParagraph title = document.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = title.createRun();
        run.setBold(true);
        run.setFontSize(16);
        run.setText("Акт приема-передачи оборудования № " + request.getId()
                + " от " + DATE_FORMAT.format(LocalDate.now()));
    }

    private void addRequestDetails(XWPFDocument document, ReceiptRequest request) {
        addText(document, "Заказчик: ФГБОУ ВО «Российский экономический университет имени Г.В. Плеханова»");
        addText(document, "Поставщик: " + emptyToDash(request.getSupplier()));
        addText(document, "Корпус поставки: " + (request.getBuilding() == null ? "-" : request.getBuilding().getName()));
        addText(document, "Основание: " + emptyToDash(request.getTitle()));
        addText(document, "Настоящий акт подтверждает передачу и приемку оборудования для постановки на учет.");
        document.createParagraph();
    }

    private void addItemsTable(XWPFDocument document, ReceiptRequest request, List<ReceiptRequestItem> items) {
        XWPFTable table = document.createTable(items.size() + 1, 8);
        table.setWidth("100%");
        table.setWidthType(TableWidthType.PCT);

        XWPFTableRow header = table.getRow(0);
        setCell(header.getCell(0), "№");
        setCell(header.getCell(1), "ШК / инв. номер");
        setCell(header.getCell(2), "Наименование");
        setCell(header.getCell(3), "Тип");
        setCell(header.getCell(4), "Стоимость, руб.");
        setCell(header.getCell(5), "Кол-во");
        setCell(header.getCell(6), "Статус");
        setCell(header.getCell(7), "Примечание");

        for (int i = 0; i < items.size(); i++) {
            ReceiptRequestItem item = items.get(i);
            XWPFTableRow row = table.getRow(i + 1);
            setCell(row.getCell(0), String.valueOf(i + 1));
            setCell(row.getCell(1), item.getExpectedInventoryNumber());
            setCell(row.getCell(2), item.getName());
            setCell(row.getCell(3), item.getType() == null ? "-" : item.getType().getName());
            setCell(row.getCell(4), money(item.getPrice()));
            setCell(row.getCell(5), "1");
            setCell(row.getCell(6), item.isAccepted() ? "Принято" : (isFinished(request) ? "Не принято" : "Ожидает приемки"));
            setCell(row.getCell(7), "");
        }
        document.createParagraph();
    }

    private void addSignatures(XWPFDocument document, ReceiptRequest request) {
        addText(document, "Дата формирования документа: " + DATE_FORMAT.format(LocalDate.now()));
        addText(document, "Документ сформировал: " + emptyToDash(request.getCreatedBy()));
        addText(document, "Представитель поставщика: __________________ / __________________");
        addText(document, "Представитель склада: __________________ / __________________");
        addText(document, "Подписано простой электронной подписью: " + java.time.LocalDateTime.now(MOSCOW)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
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

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean isFinished(ReceiptRequest request) {
        return request.getStatus() == ReceiptRequestStatus.ACCEPTED
                || request.getStatus() == ReceiptRequestStatus.PARTIALLY_ACCEPTED
                || request.getStatus() == ReceiptRequestStatus.NOT_ACCEPTED;
    }
}
