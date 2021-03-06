/*-----------------------------------------------------------------------------
 * spulib.h
 *
 * External PPU interface for SPU library.
 *---------------------------------------------------------------------------*/

#ifndef _SPULIB_H_
#define _SPULIB_H_

#include "../defs.h"
#include "../spucommand.h"
#include "../buffer.h"
#include <libspe.h>

/*-----------------------------------------------------------------------------
 * SPU management.
 *---------------------------------------------------------------------------*/

// Local store address relative to spu_data_start.
typedef uint32_t SPU_ADDRESS;

// Bookkeeping info for a SPU command group.
typedef struct _SPU_CMD_GROUP {
  uint32_t spu_id;
  uint32_t gid;
  uint32_t size;
  void *end;
} SPU_CMD_GROUP;

// Data buffer for a SPU command group.
typedef struct _SPU_CMD_GROUP_DATA {
  uint8_t data[SPU_CMD_GROUP_MAX_SIZE];
} CACHE_ALIGNED SPU_CMD_GROUP_DATA;

// Parameters needed to finish PPU data transfer commands.
typedef struct _PPU_DT_PARAMS {
  uint32_t type;
  BUFFER_CB *buf;
  uint32_t num_bytes;
} PPU_DT_PARAMS;

// Bookkeeping info for waiting PPU data transfer commands.
typedef struct _PPU_DT_CMD {
  uint32_t tag;           // Callback tag
  PPU_DT_PARAMS params;
} PPU_DT_CMD;

// Scheduler callbacks.
typedef void GENERIC_COMPLETE_CB(uint32_t tag);
typedef void SPU_COMPLETE_CB(uint32_t spu_id, uint32_t new_completed,
                             uint32_t all_completed);

typedef struct _EXTENDED_OP EXTENDED_OP;

typedef struct _SPU_INFO {
  uint32_t spu_id;

  // Start (LS address) and size of region on SPU available for program data.
  // Size includes stack.
  LS_ADDRESS data_start;
  uint32_t data_size;

  spe_program_handle_t *program;
  speid_t speid;
  // Memory address of start of region on SPU available for program data.
  // (= memory address of LS + data_start)
  void *data_addr;
  volatile spe_spu_control_area_t *control;

  // Bookkeeping info for command groups.
  SPU_CMD_GROUP cmd_groups[SPU_INT_MAX_CMD_GROUPS];
  // Data buffers for command groups.
  SPU_CMD_GROUP_DATA cmd_group_data[SPU_INT_MAX_CMD_GROUPS];
  SPU_CMD_GROUP *free_int_group;  // List of free internal command groups
  // Bitmap of SPU command IDs that library is using internally.
  uint32_t internal_mask;
  // Bitmap of completed SPU command IDs, excluding internal.
  uint32_t completed_mask;
#if CHECK
  // Bitmap of issued SPU command IDs (including internal).
  uint32_t issued_mask;           
#endif

  // Maps SPU command ID -> PPU data transfer command that is waiting on it.
  PPU_DT_CMD dt_waiting[SPU_MAX_COMMANDS];
  // Bitmap of SPU command IDs that PPU data transfer commands are waiting on.
  uint32_t dt_mask;

  // Scheduler callbacks for SPU commands and PPU data transfer commands.
  //
  // PPU data transfer commands complete and the PPU callback is run *before*
  // their corresponding SPU commands complete and the SPU callback is run.
  SPU_COMPLETE_CB *spu_complete_cb;
  GENERIC_COMPLETE_CB *ppu_dt_complete_cb;

  // List of library-handled operations involving this SPU.
  EXTENDED_OP *ext_ops;
} SPU_INFO;

extern SPU_INFO spu_info[NUM_SPU];

/*-----------------------------------------------------------------------------
 * spu_lsa
 *
 * Converts a SPU data address to a local store address.
 *---------------------------------------------------------------------------*/
static INLINE LS_ADDRESS
spu_lsa(uint32_t spu_id, SPU_ADDRESS da)
{
  return spu_info[spu_id].data_start + da;
}

/*-----------------------------------------------------------------------------
 * spu_addr
 *
 * Converts a SPU data address to a memory address.
 *---------------------------------------------------------------------------*/
static INLINE void *
spu_addr(uint32_t spu_id, SPU_ADDRESS da)
{
  return spu_info[spu_id].data_addr + da;
}

/*-----------------------------------------------------------------------------
 * spu_get_completed
 *
 * Returns bitmap of command IDs that have completed and not been cleared.
 *---------------------------------------------------------------------------*/
static INLINE uint32_t
spu_get_completed(uint32_t spu_id)
{
  return spu_info[spu_id].completed_mask;
}

/*-----------------------------------------------------------------------------
 * spu_ack_completed
 *
 * Clears IDs in a SPU's completed command ID bitmap.
 *---------------------------------------------------------------------------*/
static INLINE void
spu_ack_completed(uint32_t spu_id, uint32_t mask)
{
  check((spu_info[spu_id].completed_mask & mask) == mask);
  spu_info[spu_id].completed_mask &= ~mask;
}

/*-----------------------------------------------------------------------------
 * spu_check_completed
 *
 * Returns whether all command IDs in the specified bitmap are marked as
 * completed.
 *---------------------------------------------------------------------------*/

static INLINE bool_t
spu_check_completed(uint32_t spu_id, uint32_t mask)
{
  return ((spu_info[spu_id].completed_mask & mask) == mask);
}

/*-----------------------------------------------------------------------------
 * spu_check_and_ack_completed
 *
 * If all command IDs in the specified bitmap are marked as completed, clears
 * them and returns TRUE. Otherwise, does nothing and returns FALSE.
 *---------------------------------------------------------------------------*/

static INLINE bool_t
spu_check_and_ack_completed(uint32_t spu_id, uint32_t mask)
{
  if (spu_check_completed(spu_id, mask)) {
    spu_info[spu_id].completed_mask &= ~mask;
    return TRUE;
  } else {
    return FALSE;
  }
}

// Clears command group #gid for SPU #spu_id and returns pointer to its
// bookkeeping structure.
SPU_CMD_GROUP *spu_new_group(uint32_t spu_id, uint32_t gid);
// Issues all commands in a command group to a SPU. Command structures are
// copied to SPU local store starting at data address da.
void spu_issue_group(uint32_t spu_id, uint32_t gid, SPU_ADDRESS da);

/*-----------------------------------------------------------------------------
 * spu_get_group
 *
 * Returns pointer to bookkeeping structure for a SPU command group.
 *---------------------------------------------------------------------------*/
static INLINE SPU_CMD_GROUP *
spu_get_group(uint32_t spu_id, uint32_t gid)
{
  pcheck(gid < SPU_MAX_CMD_GROUPS);
  return &spu_info[spu_id].cmd_groups[gid];
}

/*-----------------------------------------------------------------------------
 * spu_first_command
 *
 * Returns pointer to first command in the specified command group, NULL if
 * group is empty.
 *---------------------------------------------------------------------------*/
static INLINE SPU_CMD_HEADER *
spu_first_command(SPU_CMD_GROUP *g)
{
  if (g->size == 0) {
    return NULL;
  } else {
    return (SPU_CMD_HEADER *)(g->end - g->size);
  }
}

/*-----------------------------------------------------------------------------
 * spu_next_command
 *
 * Returns pointer to command after cmd in the specified command group, NULL if
 * cmd is last command.
 *---------------------------------------------------------------------------*/
static INLINE SPU_CMD_HEADER *
spu_next_command(SPU_CMD_GROUP *g, SPU_CMD_HEADER *cmd)
{
  SPU_CMD_HEADER *next_cmd;
  next_cmd = (SPU_CMD_HEADER *)((void *)cmd + spu_cmd_get_size(cmd));
  if (next_cmd == g->end) {
    return NULL;
  } else {
    return next_cmd;
  }
}

/*-----------------------------------------------------------------------------
 * Stubs for initializing SPU command structures.
 *
 * These functions add a new command to a command group.
 *
 * Parameters:
 * - Group to add command to.
 * - Actual parameters for command.
 * - Command ID.
 * - Number of (backward) dependencies.
 * - IDs of dependencies.
 *---------------------------------------------------------------------------*/

#define DECLARE_SPU_COMMAND(name, ...) \
  SPU_CMD_HEADER *spu_##name(SPU_CMD_GROUP *g, ##__VA_ARGS__,                 \
                             uint32_t cmd_id, uint32_t num_deps, ...)

// Filter commands.
DECLARE_SPU_COMMAND(load_data,
                    SPU_ADDRESS dest_da, void *src_addr, uint32_t num_bytes);
DECLARE_SPU_COMMAND(filter_load,
                    SPU_ADDRESS filt, SPU_FILTER_DESC *desc);
DECLARE_SPU_COMMAND(filter_unload,
                    SPU_ADDRESS filt);
#if CHECK
DECLARE_SPU_COMMAND(filter_detach_all,
                    SPU_ADDRESS filt);
#endif
DECLARE_SPU_COMMAND(filter_attach_input,
                    SPU_ADDRESS filt, uint32_t tape_id, SPU_ADDRESS buf_data);
DECLARE_SPU_COMMAND(filter_attach_output,
                    SPU_ADDRESS filt, uint32_t tape_id, SPU_ADDRESS buf_data);
DECLARE_SPU_COMMAND(filter_run,
                    SPU_ADDRESS filt, uint32_t iters, uint32_t loop_iters);
// Buffer commands.
DECLARE_SPU_COMMAND(buffer_alloc,
                    SPU_ADDRESS buf_data, uint32_t size, uint32_t data_offset);
DECLARE_SPU_COMMAND(buffer_align,
                    SPU_ADDRESS buf_data, uint32_t data_offset);
// Data transfer commands.
DECLARE_SPU_COMMAND(dt_out_front,
                    SPU_ADDRESS buf_data, void *dest_buf_data,
                    uint32_t num_bytes);
#define spu_dt_out_front_ppu(g, buf_data, dest_buf, num_bytes, cmd_id,        \
                             num_deps, ...)                                   \
  spu_dt_out_front_ppu_ex(g, buf_data, dest_buf, num_bytes, FALSE, cmd_id,    \
                          num_deps, ##__VA_ARGS__)
#define spu_dt_out_front_proc(g, buf_data, dest_ppu, dest_buf_ptr, num_bytes, \
                              cmd_id, num_deps, ...)                          \
  ((dest_ppu) ?                                                               \
   spu_dt_out_front_ppu(g, buf_data, (BUFFER_CB *)(dest_buf_ptr), num_bytes,  \
                        cmd_id, num_deps, ##__VA_ARGS__) :                    \
   spu_dt_out_front(g, buf_data, dest_buf_ptr, num_bytes, cmd_id,             \
                    num_deps, ##__VA_ARGS__))
DECLARE_SPU_COMMAND(dt_out_front_ppu_ex,
                    SPU_ADDRESS buf_data, BUFFER_CB *dest_buf,
                    uint32_t num_bytes, bool_t tail_overlaps);
DECLARE_SPU_COMMAND(dt_in_back,
                    SPU_ADDRESS buf_data, void *src_buf_data,
                    uint32_t src_buf_size, uint32_t num_bytes);
DECLARE_SPU_COMMAND(dt_in_back_ppu,
                    SPU_ADDRESS buf_data, BUFFER_CB *src_buf,
                    uint32_t num_bytes);
#define spu_dt_in_back_proc(g, buf_data, src_ppu, src_buf_ptr,                \
                            src_spu_buf_size, num_bytes, cmd_id, num_deps,    \
                            ...)                                              \
  ((src_ppu) ?                                                                \
   spu_dt_in_back_ppu(g, buf_data, (BUFFER_CB *)(src_buf_ptr), num_bytes,     \
                      cmd_id, num_deps, ##__VA_ARGS__) :                      \
   spu_dt_in_back(g, buf_data, src_buf_ptr, src_spu_buf_size, num_bytes,      \
                  cmd_id, num_deps, ##__VA_ARGS__))
// Misc commands.
DECLARE_SPU_COMMAND(null);
DECLARE_SPU_COMMAND(call_func,
                    LS_ADDRESS func);
#if SPU_STATS_ENABLE
// Stats commands.
DECLARE_SPU_COMMAND(stats_print);
DECLARE_SPU_COMMAND(stats_update);
#endif

#undef DECLARE_SPU_COMMAND

// Common signature for spu_dt_out_front_spu/ppu.
typedef SPU_CMD_HEADER *(*SPU_DT_OUT_FRONT_FUNC)
  (SPU_CMD_GROUP *g, SPU_ADDRESS buf_data, void *dest_buf_data,
   uint32_t dest_buf_size, uint32_t num_bytes, uint32_t cmd_id,
   uint32_t num_deps, ...);

/*-----------------------------------------------------------------------------
 * PPU commands.
 *
 * PPU commands are not queued and run immediately when these functions are
 * called.
 *---------------------------------------------------------------------------*/

// Buffer commands.
//
// alloc and dealloc actually allocate/free memory.
BUFFER_CB *alloc_buffer(uint32_t size, bool_t circular, uint32_t data_offset);
void *alloc_buffer_ex(uint32_t size, bool_t circular, uint32_t data_offset);
void align_buffer(BUFFER_CB *buf, uint32_t data_offset);
void dealloc_buffer(BUFFER_CB *buf);

void init_buffer(BUFFER_CB *buf, void *buf_data, uint32_t size,
                 bool_t circular, uint32_t data_offset);
void duplicate_buffer(BUFFER_CB *dest, BUFFER_CB *src);

void *malloc_buffer_data(uint32_t size);
void *malloc_aligned(uint32_t size, uint32_t alignment);
void free_aligned(void *data);
void touch_pages(void *data, uint32_t size);

// Data transfer commands.
//
// The currently (or next, if the ID is not yet issued) issued command with ID
// spu_cmd_id on the corresponding SPU must be the command that handles the
// other end of the data transfer.
//
// tag is an arbitrary tag that is passed to the callback when the data
// transfer completes.
void dt_in_back(BUFFER_CB *buf, uint32_t src_spu, SPU_ADDRESS src_buf_data,
                uint32_t num_bytes, uint32_t spu_cmd_id, uint32_t tag);
void dt_out_front(BUFFER_CB *buf, uint32_t dest_spu, SPU_ADDRESS dest_buf_data,
                  uint32_t num_bytes, uint32_t spu_cmd_id, uint32_t tag);

// For dt_in_back_ex, all except the last corresponding SPU dt_out_front_ppu
// command must transfer to a qword boundary.
void dt_in_back_ex(BUFFER_CB *buf, uint32_t src_spu, SPU_ADDRESS src_buf_data,
                   uint32_t num_bytes);
void dt_out_front_ex(BUFFER_CB *buf, uint32_t dest_spu,
                     SPU_ADDRESS dest_buf_data, uint32_t num_bytes);

#if DT_ALLOW_UNALIGNED
void finish_dt_in_back_ex_head(BUFFER_CB *buf, bool_t tail_overlaps);
void finish_dt_in_back_ex_tail(BUFFER_CB *buf);
#else
static INLINE void finish_dt_in_back_ex_head(BUFFER_CB *buf UNUSED,
                                             bool_t tail_overlaps UNUSED) {}
static INLINE void finish_dt_in_back_ex_tail(BUFFER_CB *buf UNUSED) {}
#endif

/*-----------------------------------------------------------------------------
 * Library-handled operations.
 *---------------------------------------------------------------------------*/

#include "ext.h"

/*-----------------------------------------------------------------------------
 * Misc.
 *---------------------------------------------------------------------------*/

bool_t spulib_init();
// Polls SPUs for command completion messages in round-robin fashion. Returns
// TRUE when a completion message is received, or FALSE after polling all SPUs.
bool_t spulib_poll();

#define spulib_poll_while(exp) \
  while (exp) {                                                               \
    while (!spulib_poll());                                                   \
  }

// Waits until all command IDs in the specified bitmap are marked as completed.
// This function internally polls. Callbacks are run as normal.
void spulib_wait(uint32_t spu_id, uint32_t mask);

void spulib_set_all_spu_complete_cb(SPU_COMPLETE_CB *cb);
void spulib_set_all_ppu_dt_complete_cb(GENERIC_COMPLETE_CB *cb);

#if SPU_STATS_ENABLE
void spulib_start_stats(uint32_t num_spu);
void spulib_print_stats(uint32_t num_spu);
#else
static INLINE void spulib_start_stats(uint32_t num_spu UNUSED) {}
static INLINE void spulib_print_stats(uint32_t num_spu UNUSED) {}
#endif

#endif
