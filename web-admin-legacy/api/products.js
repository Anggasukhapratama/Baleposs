const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    if (req.method === "POST") {
      const chunks = [];
      for await (const chunk of req) chunks.push(chunk);
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8") || "{}");
      const margin = 10;
      await query(
        `INSERT INTO products
           (sku, name, product_type_code, hpp, margin_percent, selling_price, stock, is_active)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 1)`,
        [
          String(body.sku || "").trim(),
          String(body.name || "").trim(),
          String(body.type || "PERIPHERAL"),
          Number(body.hpp || 0),
          margin,
          Number(body.sellingPrice || 0),
          Number(body.stock || 0),
        ]
      );
      return sendJson(res, 200, { ok: true });
    }

    const rows = await query(
      `SELECT p.sku, p.name, p.product_type_code AS type,
              COALESCE(c.name, '-') AS category,
              COALESCE(b.name, '-') AS brand,
              p.stock::integer AS stock,
              p.selling_price::bigint AS selling_price
       FROM products p
       LEFT JOIN categories c ON c.id = p.category_id
       LEFT JOIN brands b ON b.id = p.brand_id
       WHERE p.is_active = 1
       ORDER BY p.name ASC
       LIMIT 500`,
      []
    );
    return sendJson(res, 200, { products: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
