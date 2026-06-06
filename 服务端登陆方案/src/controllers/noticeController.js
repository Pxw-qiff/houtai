/**
 * 通知管理控制器
 */
const { v4: uuidv4 } = require('uuid');
const { getPool } = require('../config/database');
const { handleApiError } = require('../middleware/error');

/**
 * 获取通知列表
 */
const getNotices = async (req, res) => {
    try {
        const { page, limit } = req.query;
        const pageNum = parseInt(page) || 1;
        const limitNum = parseInt(limit) || 20;
        const offset = (pageNum - 1) * limitNum;

        const connection = await getPool().getConnection();

        try {
            // 构建查询条件
            const { search, tag, start_date, end_date } = req.query;
            let whereClause = 'WHERE 1=1';
            const params = [];

            if (search) {
                whereClause += ' AND (title LIKE ? OR content LIKE ?)';
                params.push(`%${search}%`, `%${search}%`);
            }

            if (tag) {
                whereClause += ' AND type = ?';
                params.push(tag);
            }

            if (start_date) {
                whereClause += ' AND created_at >= ?';
                params.push(start_date);
            }

            if (end_date) {
                whereClause += ' AND created_at <= ?';
                params.push(`${end_date} 23:59:59`); // 包含结束日期的全天
            }

            // 获取总数
            const [countResult] = await connection.execute(
                `SELECT COUNT(*) as total FROM notices ${whereClause}`,
                params
            );

            // 获取通知列表
            const [notices] = await connection.query(
                `SELECT * FROM notices ${whereClause} ORDER BY created_at DESC LIMIT ? OFFSET ?`,
                [...params, limitNum, offset]
            );

            res.json({
                success: true,
                data: notices,
                pagination: {
                    page: pageNum,
                    limit: limitNum,
                    total: countResult[0].total,
                    pages: Math.ceil(countResult[0].total / limitNum)
                }
            });
            
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '获取通知列表失败');
    }
};

/**
 * 创建通知
 */
const createNotice = async (req, res) => {
    try {
        const { title, content, type = 'info', priority = 'normal' } = req.body;

        if (!title || !content) {
            return res.status(400).json({
                success: false,
                error: '标题和内容不能为空'
            });
        }

        const connection = await getPool().getConnection();

        try {
            const [result] = await connection.query(
                'INSERT INTO notices (notice_id, title, content, type, priority, status, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())',
                [uuidv4(), title, content, type, priority, 'active']
            );

            res.json({
                success: true,
                message: '通知创建成功',
                data: { notice_id: result.insertId }
            });
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '创建通知失败');
    }
};

/**
 * 更新通知
 */
const updateNotice = async (req, res) => {
    try {
        const { noticeId } = req.params;
        const { title, content, type, priority } = req.body;

        const connection = await getPool().getConnection();

        try {
            // 如果 priority 为空，则使用默认值 'normal'，避免数据库报错
            const safePriority = priority || 'normal';

            const [result] = await connection.query(
                'UPDATE notices SET title = ?, content = ?, type = ?, priority = ?, updated_at = NOW() WHERE notice_id = ?',
                [title, content, type, safePriority, noticeId]
            );

            if (result.affectedRows === 0) {
                return res.status(404).json({
                    success: false,
                    error: '通知不存在'
                });
            }

            res.json({
                success: true,
                message: '通知更新成功'
            });
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '更新通知失败');
    }
};

/**
 * 删除通知
 */
const deleteNotice = async (req, res) => {
    try {
        const { noticeId } = req.params;

        const connection = await getPool().getConnection();

        try {
            const [result] = await connection.execute(
                'DELETE FROM notices WHERE notice_id = ?',
                [noticeId]
            );

            if (result.affectedRows === 0) {
                return res.status(404).json({
                    success: false,
                    error: '通知不存在'
                });
            }

            res.json({
                success: true,
                message: '通知删除成功'
            });
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '删除通知失败');
    }
};

module.exports = {
    getNotices,
    createNotice,
    updateNotice,
    deleteNotice
};
