package com.baletpos.dao;

import com.baletpos.config.FirestoreHelper;
import com.baletpos.model.Product;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProductDAO {
    private static final Logger logger = LoggerFactory.getLogger(ProductDAO.class);
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String COLLECTION = "products";

    public List<Product> findAll() {
        List<Product> products = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .get();

            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                products.add(mapDocumentToProduct(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding all products", e);
        }
        return products;
    }

    public List<Product> findAllPaged(int limit, int offset, String searchQuery, String categoryFilter) {
        // Karena Firestore tidak punya fitur LIKE SQL atau cursor paging dinamis yang mudah, 
        // kita menggunakan in-memory filtering untuk dataset kecil/menengah.
        List<Product> allFiltered = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            CollectionReference ref = db.collection(COLLECTION);
            Query query = ref.whereEqualTo("is_active", 1);
            
            if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
                query = query.whereEqualTo("product_type_code", categoryFilter);
            }
            
            ApiFuture<QuerySnapshot> future = query.get();
            String lowerSearch = searchQuery != null ? searchQuery.toLowerCase() : "";
            
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Product p = mapDocumentToProduct(doc);
                if (lowerSearch.isBlank() || 
                    p.getName().toLowerCase().contains(lowerSearch) || 
                    p.getSku().toLowerCase().contains(lowerSearch)) {
                    allFiltered.add(p);
                }
            }
            
            allFiltered.sort((p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));
            
            int start = Math.min(offset, allFiltered.size());
            int end = Math.min(start + limit, allFiltered.size());
            return allFiltered.subList(start, end);
            
        } catch (Exception e) {
            logger.error("Error finding paged products", e);
        }
        return new ArrayList<>();
    }

    public int countFiltered(String searchQuery, String categoryFilter) {
        // Cara paling aman dengan in-memory untuk menyamai fungsi findAllPaged
        try {
            Firestore db = FirestoreHelper.getDb();
            Query query = db.collection(COLLECTION).whereEqualTo("is_active", 1);
            
            if (categoryFilter != null && !categoryFilter.equals("SEMUA")) {
                query = query.whereEqualTo("product_type_code", categoryFilter);
            }
            
            ApiFuture<QuerySnapshot> future = query.get();
            String lowerSearch = searchQuery != null ? searchQuery.toLowerCase() : "";
            int count = 0;
            
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Product p = mapDocumentToProduct(doc);
                if (lowerSearch.isBlank() || 
                    p.getName().toLowerCase().contains(lowerSearch) || 
                    p.getSku().toLowerCase().contains(lowerSearch)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            logger.error("Error counting products", e);
        }
        return 0;
    }

    public List<Product> search(String query) {
        List<Product> products = new ArrayList<>();
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("is_active", 1)
                    .get();

            String lowerSearch = query != null ? query.toLowerCase() : "";
            for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
                Product p = mapDocumentToProduct(doc);
                if (p.getName().toLowerCase().contains(lowerSearch) || 
                    p.getSku().toLowerCase().contains(lowerSearch)) {
                    products.add(p);
                }
            }
        } catch (Exception e) {
            logger.error("Error searching products with query: {}", query, e);
        }
        return products;
    }

    public Optional<Product> findBySku(String sku) {
        try {
            Firestore db = FirestoreHelper.getDb();
            ApiFuture<QuerySnapshot> future = db.collection(COLLECTION)
                    .whereEqualTo("sku", sku)
                    .whereEqualTo("is_active", 1)
                    .limit(1)
                    .get();

            QuerySnapshot querySnapshot = future.get();
            if (!querySnapshot.isEmpty()) {
                return Optional.of(mapDocumentToProduct(querySnapshot.getDocuments().get(0)));
            }
        } catch (Exception e) {
            logger.error("Error finding product by SKU: {}", sku, e);
        }
        return Optional.empty();
    }
    
    public Optional<Product> findById(Long id) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentSnapshot doc = db.collection(COLLECTION).document(String.valueOf(id)).get().get();
            if (doc.exists()) {
                return Optional.of(mapDocumentToProduct(doc));
            }
        } catch (Exception e) {
            logger.error("Error finding product by id: {}", id, e);
        }
        return Optional.empty();
    }

    public void save(Product product) {
        if (product.getId() == null) {
            insert(product);
        } else {
            update(product);
        }
    }

    private void insert(Product product) {
        BigDecimal hpp = product.getHpp();
        double margin = product.getMarginPercent();
        BigDecimal marginAmount = hpp.multiply(BigDecimal.valueOf(margin)).divide(BigDecimal.valueOf(100),
                java.math.RoundingMode.HALF_UP);
        BigDecimal sellingPrice = hpp.add(marginAmount).setScale(0, java.math.RoundingMode.HALF_UP);
        product.setSellingPrice(sellingPrice);

        try {
            Firestore db = FirestoreHelper.getDb();
            Long newId = FirestoreHelper.getNextId(COLLECTION);
            product.setId(newId);
            product.setActive(true);
            product.setCreatedAt(LocalDateTime.now());
            product.setUpdatedAt(LocalDateTime.now());

            db.collection(COLLECTION).document(String.valueOf(newId)).set(productToMap(product)).get();
            logger.info("Product inserted: sku={}, id={}", product.getSku(), product.getId());
        } catch (Exception e) {
            logger.error("Error inserting product", e);
            throw new RuntimeException("Gagal menyimpan produk baru", e);
        }
    }

    public void updateImagePath(Long productId, String imagePath) {
        try {
            Firestore db = FirestoreHelper.getDb();
            Map<String, Object> update = new HashMap<>();
            update.put("image_path", imagePath);
            update.put("updated_at", LocalDateTime.now().format(DB_DATE_FMT));
            db.collection(COLLECTION).document(String.valueOf(productId)).update(update).get();
            logger.info("Image path updated: productId={}, imagePath={}", productId, imagePath);
        } catch (Exception e) {
            logger.error("Error updating image path", e);
            throw new RuntimeException("Gagal menyimpan foto produk", e);
        }
    }

    private void update(Product product) {
        BigDecimal hpp = product.getHpp();
        double margin = product.getMarginPercent();
        BigDecimal marginAmount = hpp.multiply(BigDecimal.valueOf(margin)).divide(BigDecimal.valueOf(100),
                java.math.RoundingMode.HALF_UP);
        BigDecimal sellingPrice = hpp.add(marginAmount).setScale(0, java.math.RoundingMode.HALF_UP);
        product.setSellingPrice(sellingPrice);
        product.setUpdatedAt(LocalDateTime.now());

        try {
            Firestore db = FirestoreHelper.getDb();
            db.collection(COLLECTION).document(String.valueOf(product.getId())).set(productToMap(product)).get();
            logger.info("[DB] Product updated: id={}, image_path={}", product.getId(), product.getImagePath());
        } catch (Exception e) {
            logger.error("Error updating product", e);
            throw new RuntimeException("Error updating product: " + e.getMessage(), e);
        }
    }

    public void updateStock(Long productId, int quantityChange) {
        try {
            Firestore db = FirestoreHelper.getDb();
            DocumentReference docRef = db.collection(COLLECTION).document(String.valueOf(productId));
            docRef.update("stock", FieldValue.increment(quantityChange)).get();
        } catch (Exception e) {
            logger.error("Error updating stock for product id: {}", productId, e);
            throw new RuntimeException("Error updating stock", e);
        }
    }

    private Map<String, Object> productToMap(Product p) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", p.getId());
        map.put("sku", p.getSku());
        map.put("name", p.getName());
        map.put("product_type_code", p.getProductType() != null ? p.getProductType().name() : null);
        map.put("category_id", p.getCategoryId());
        map.put("brand_id", p.getBrandId());
        map.put("hpp", p.getHpp() != null ? p.getHpp().longValue() : 0L);
        map.put("margin_percent", p.getMarginPercent());
        map.put("selling_price", p.getSellingPrice() != null ? p.getSellingPrice().longValue() : 0L);
        map.put("stock", p.getStock());
        map.put("description", p.getDescription());
        map.put("image_path", p.getImagePath());
        map.put("is_active", p.isActive() ? 1 : 0);

        if (p.getCreatedAt() != null) {
            map.put("created_at", p.getCreatedAt().format(DB_DATE_FMT));
        }
        if (p.getUpdatedAt() != null) {
            map.put("updated_at", p.getUpdatedAt().format(DB_DATE_FMT));
        }
        
        return map;
    }

    private Product mapDocumentToProduct(DocumentSnapshot doc) {
        Product p = new Product();
        p.setId(doc.getLong("id"));
        p.setSku(doc.getString("sku"));
        p.setName(doc.getString("name"));
        
        String typeStr = doc.getString("product_type_code");
        if (typeStr != null) {
            p.setProductType(Product.ProductType.valueOf(typeStr));
        }
        
        p.setCategoryId(doc.getLong("category_id"));
        p.setBrandId(doc.getLong("brand_id"));
        
        Long hpp = doc.getLong("hpp");
        p.setHpp(hpp != null ? BigDecimal.valueOf(hpp) : BigDecimal.ZERO);
        
        Double margin = doc.getDouble("margin_percent");
        p.setMarginPercent(margin != null ? margin : 0.0);
        
        Long sellingPrice = doc.getLong("selling_price");
        p.setSellingPrice(sellingPrice != null ? BigDecimal.valueOf(sellingPrice) : BigDecimal.ZERO);
        
        Long stock = doc.getLong("stock");
        p.setStock(stock != null ? stock.intValue() : 0);
        
        p.setDescription(doc.getString("description"));
        p.setImagePath(doc.getString("image_path"));
        
        Long isActive = doc.getLong("is_active");
        p.setActive(isActive != null && isActive == 1);

        // Firestore cannot JOIN natively, set dummy values for categoryName and brandName for now
        p.setCategoryName(p.getCategoryId() != null ? "Kategori ID: " + p.getCategoryId() : "");
        p.setBrandName(p.getBrandId() != null ? "Merek ID: " + p.getBrandId() : "");

        String createdAtStr = doc.getString("created_at");
        if (createdAtStr != null) {
            p.setCreatedAt(LocalDateTime.parse(createdAtStr, DB_DATE_FMT));
        }

        String updatedAtStr = doc.getString("updated_at");
        if (updatedAtStr != null) {
            p.setUpdatedAt(LocalDateTime.parse(updatedAtStr, DB_DATE_FMT));
        }

        return p;
    }
}


