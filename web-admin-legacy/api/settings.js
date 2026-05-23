const { query, sendJson, handleError, requireFinance } = require("./_db");

module.exports = async function handler(req, res) {
  if (!requireFinance(req, res)) return;
  try {
    const rows = await query(
      `SELECT setting_key AS key, setting_value AS value, description
       FROM settings
       ORDER BY setting_key ASC`,
      []
    );
    return sendJson(res, 200, { settings: rows });
  } catch (error) {
    return handleError(res, error);
  }
};
