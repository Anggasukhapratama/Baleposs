const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    if (req.method === "POST") {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
      const sku = String(body.sku || "").trim();
      const physical = Number(body.physical || 0);
      const note = String(body.note || "Stock opname web");

      const productRows = await query("SELECT id, stock FROM products WHERE sku = $1 LIMIT 1", [sku]);
      const product = productRows[0];
      if (!product) return sendJson(res, 404, { error: "Produk tidak ditemukan" });

      const diff = physical - Number(product.stock || 0);
      if (diff !== 0) {
        const userRows = await query(
          "SELECT id FROM users WHERE role = 'ADMIN_KEUANGAN' ORDER BY id LIMIT 1",
          []
        );
        const createdBy = userRows[0]?.id;
        if (!createdBy) return sendJson(res, 400, { error: "User ADMIN_KEUANGAN belum ada" });

        await query(
          `INSERT INTO stock_adjustments
             (adjustment_number, product_id, adjustment_date, quantity_change, reason, notes, created_by)
           VALUES
             ('ADJ-' || to_char(NOW(), 'YYYYMMDDHH24MISSMS'), $1, NOW(), $2, 'OPNAME', $3, $4)`,
          [product.id, diff, note, createdBy]
        );
        await query(
          `INSERT INTO stock_movements
             (product_id, movement_type, reference_type, reference_id, quantity_change,
              stock_before, stock_after, notes, created_by)
           VALUES ($1, 'ADJUSTMENT', 'STOCK_OPNAME_WEB', NULL, $2, $3, $4, $5, $6)`,
          [product.id, diff, product.stock, physical, note, createdBy]
        );
        await query("UPDATE products SET stock = $1, updated_at = LOCALTIMESTAMP(0) WHERE id = $2", [physical, product.id]);
      }

      return sendJson(res, 200, { ok: true, sku, physical, diff });
    }

    const rows = await query(
      `SELECT sku, name, product_type_code AS type, stock::integer AS system,
              stock::integer AS physical, hpp::bigint, selling_price::bigint
       FROM products
       WHERE is_active = 1
       ORDER BY stock ASC, name ASC
       LIMIT 500`,
      []
    );
    return sendJson(res, 200, { stock: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
