package com.baletpos.service;

import com.baletpos.dao.SaleDAO;
import com.baletpos.model.Sale;

import java.time.LocalDate;
import java.util.List;

public class SaleService {
    private final SaleDAO saleDAO = new SaleDAO();

    public List<Sale> getAllSales() {
        return saleDAO.findAll();
    }

    public List<Sale> getSalesByDateRange(LocalDate startDate, LocalDate endDate) {
        return saleDAO.findByDateRange(startDate, endDate);
    }

    public List<Sale> searchSales(LocalDate startDate, LocalDate endDate, String query, int limit, int offset) {
        return saleDAO.searchSales(startDate, endDate, query, limit, offset);
    }

    public long countSales(LocalDate startDate, LocalDate endDate, String query) {
        return saleDAO.countSales(startDate, endDate, query);
    }

    public Sale findById(Long id) {
        return saleDAO.findById(id);
    }

    public Sale findByInvoiceNumber(String invoiceNumber) {
        return saleDAO.findByInvoiceNumber(invoiceNumber);
    }

    public void voidSale(Long saleId, Long voidedBy, String voidReason) throws Exception {
        saleDAO.voidSale(saleId, voidedBy, voidReason);
    }
}

