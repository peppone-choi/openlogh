-- Fix: V10 incorrectly changed board_comment.board_id FK to reference message(id)
-- The correct reference is board(id) as originally defined in V4
ALTER TABLE board_comment
    DROP CONSTRAINT IF EXISTS board_comment_board_id_fkey;

ALTER TABLE board_comment
    ADD CONSTRAINT board_comment_board_id_fkey
    FOREIGN KEY (board_id) REFERENCES board(id) ON DELETE CASCADE;
