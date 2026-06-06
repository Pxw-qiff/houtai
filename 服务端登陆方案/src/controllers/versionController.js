/**
 * 更新日志管理控制器
 */
const { v4: uuidv4 } = require('uuid');
const { getPool } = require('../config/database');
const { handleApiError } = require('../middleware/error');

/**
 * 获取更新日志列表
 */
const getVersions = async (req, res) => {
    try {
        const { page, limit, platform, version, date_start, date_end } = req.query;
        const pageNum = parseInt(page) || 1;
        const limitNum = parseInt(limit) || 20;
        const offset = (pageNum - 1) * limitNum;

        const connection = await getPool().getConnection();

        try {
            let whereClause = 'WHERE 1=1';
            const params = [];

            if (platform) {
                whereClause += ' AND platform = ?';
                params.push(platform);
            }
            if (version) {
                whereClause += ' AND version LIKE ?';
                params.push(`%${version}%`);
            }

            if (date_start) {
                whereClause += ' AND date >= ?';
                params.push(date_start);
            }
            if (date_end) {
                whereClause += ' AND date <= ?';
                params.push(date_end);
            }

            // 获取总数
            const [countResult] = await connection.query(
                `SELECT COUNT(*) as total FROM update_versions ${whereClause}`,
                params
            );

            // 获取版本列表
            // 按日期倒序 -> 版本号倒序 -> 创建时间倒序
            const [versions] = await connection.query(
                `SELECT * FROM update_versions ${whereClause} ORDER BY STR_TO_DATE(date, '%Y.%m.%d') DESC, version DESC, created_at DESC LIMIT ? OFFSET ?`,
                [...params, limitNum, offset]
            );

            // 为每个版本查询 items (预览前5条)
            for (const ver of versions) {
                const [items] = await connection.query(
                    'SELECT item_id, type, text FROM update_items WHERE version_id = ? ORDER BY created_at ASC LIMIT 5',
                    [ver.version_id]
                );
                ver.items = items;
            }

            res.json({
                success: true,
                data: versions,
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
        handleApiError(res, error, '获取更新日志列表失败');
    }
};

/**
 * 获取单个更新日志详情
 */
const getVersionDetail = async (req, res) => {
    try {
        const { versionId } = req.params;
        const connection = await getPool().getConnection();

        try {
            const [versions] = await connection.query(
                'SELECT * FROM update_versions WHERE version_id = ?',
                [versionId]
            );

            if (versions.length === 0) {
                return res.status(404).json({ success: false, error: '更新日志不存在' });
            }

            const versionData = versions[0];
            const [items] = await connection.query(
                'SELECT * FROM update_items WHERE version_id = ? ORDER BY created_at ASC',
                [versionId]
            );
            versionData.items = items;

            res.json({ success: true, data: versionData });
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '获取更新日志详情失败');
    }
};

/**
 * 创建更新日志
 */
const createVersion = async (req, res) => {
    try {
        const { version, date, platform, items = [] } = req.body;

        if (!version || !date) {
            return res.status(400).json({ success: false, error: '版本号和日期不能为空' });
        }

        const ALLOWED_PLATFORMS = ['windows', 'mac', 'beta'];
        if (platform && !ALLOWED_PLATFORMS.includes(platform)) {
            return res.status(400).json({ success: false, error: '无效的平台类型' });
        }

        const connection = await getPool().getConnection();

        try {
            await connection.beginTransaction();

            const versionId = uuidv4();

            // 插入主表
            await connection.query(
                'INSERT INTO update_versions (version_id, version, date, platform, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())',
                [versionId, version, date, platform || 'windows']
            );

            // 插入详情项
            if (Array.isArray(items) && items.length > 0) {
                for (const item of items) {
                    const itemId = uuidv4(); // 必须手动生成 UUID
                    await connection.query(
                        'INSERT INTO update_items (item_id, version_id, type, text, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())',
                        [itemId, versionId, item.type || 'feat', item.text]
                    );
                }
            }

            await connection.commit();

            res.json({
                success: true,
                message: '更新日志创建成功',
                data: { version_id: versionId }
            });
        } catch (error) {
            await connection.rollback();
            throw error;
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '创建更新日志失败');
    }
};

/**
 * 更新更新日志
 */
const updateVersion = async (req, res) => {
    try {
        const { versionId } = req.params;
        const { version, date, platform, items } = req.body;

        if (!version || !date) {
            return res.status(400).json({ success: false, error: '版本号和日期不能为空' });
        }

        const ALLOWED_PLATFORMS = ['windows', 'mac', 'beta'];
        if (platform && !ALLOWED_PLATFORMS.includes(platform)) {
            return res.status(400).json({ success: false, error: '无效的平台类型' });
        }

        const connection = await getPool().getConnection();

        try {
            await connection.beginTransaction();

            // 1. 更新主表
            const [updateResult] = await connection.query(
                'UPDATE update_versions SET version = ?, date = ?, platform = ?, updated_at = NOW() WHERE version_id = ?',
                [version, date, platform || 'windows', versionId]
            );

            if (updateResult.affectedRows === 0) {
                await connection.rollback();
                return res.status(404).json({ success: false, error: '更新日志不存在' });
            }

            // 2. 更新 items (先删后加策略)
            if (Array.isArray(items)) {
                // 删除旧 items
                await connection.query('DELETE FROM update_items WHERE version_id = ?', [versionId]);

                // 插入新 items
                if (items.length > 0) {
                    for (const item of items) {
                        const itemId = uuidv4();
                        await connection.query(
                            'INSERT INTO update_items (item_id, version_id, type, text, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())',
                            [itemId, versionId, item.type || 'feat', item.text]
                        );
                    }
                }
            }

            await connection.commit();
            res.json({ success: true, message: '更新成功' });
        } catch (error) {
            await connection.rollback();
            throw error;
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '更新日志失败');
    }
};

/**
 * 删除更新日志
 */
const deleteVersion = async (req, res) => {
    try {
        const { versionId } = req.params;
        const connection = await getPool().getConnection();

        try {
            // 由于设置了 ON DELETE CASCADE，直接删除主表即可自动删除关联子项
            const [result] = await connection.query('DELETE FROM update_versions WHERE version_id = ?', [versionId]);

            if (result.affectedRows === 0) {
                return res.status(404).json({ success: false, error: '更新日志不存在' });
            }

            res.json({ success: true, message: '删除成功' });
        } finally {
            connection.release();
        }
    } catch (error) {
        handleApiError(res, error, '删除更新日志失败');
    }
};

module.exports = {
    getVersions,
    getVersionDetail,
    createVersion,
    updateVersion,
    deleteVersion
};
