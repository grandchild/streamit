
#include <memsocket.h>

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sched.h>

memsocket::memsocket() {
  tail = 0;
  head = 0;
  data_buffer = (char*)malloc(BUFFER_SIZE_BYTES);
  item_size = 1;
  size = BUFFER_SIZE_BYTES;
}


void memsocket::set_item_size(int item_size) {

  // set_item_size will be called 2-times. 
  // must make sure the second call does not alter the state

  if (item_size == this->item_size) return; 

  tail = 0;
  head = 0;
  this->item_size = item_size;
  size = BUFFER_SIZE_BYTES / item_size;
  printf("Set Item size %d!\n", this->item_size);
}
  
int memsocket::eof() { return 0; }

int memsocket::get_fd() { return -1; }

void memsocket::close() {}


bool memsocket::data_available() { 
  return (head != tail);
}


int memsocket::write_chunk(char *buf, int len) {
  if (len % item_size != 0) {
    perror("memsocket: wrong item size!\n");
    exit(0);
  }

  int n = len / item_size;
  int n_head;

  while (n > 0) {

    n_head = (head + 1) % size;
    while (n_head == tail) { sched_yield(); }
    memcpy(&data_buffer[head * item_size], buf, item_size);
    head = n_head;

    buf += item_size;
    n--;
  }
} 


int memsocket::read_chunk(char *buf, int len) {
  if (len % item_size != 0) {
    perror("memsocket: wrong item size!\n");
    exit(0);
  }

  int n = len / item_size;

  while (n > 0) {

    while (tail == head) { sched_yield(); }
    memcpy(buf, &data_buffer[tail * item_size], item_size);
    tail = (tail + 1) % size;

    buf += item_size;
    n--;
  }
}


int memsocket::write_item(char *buf) {
  int n_head = (head + 1) % size;
  while (n_head == tail) { sched_yield(); }
  
  memcpy(&data_buffer[head * item_size], buf, item_size);
  //*(int*)&data_buffer[head] = *(int*)buf;

  head = n_head;
} 


int memsocket::read_item(char *buf) {
  while (tail == head) { sched_yield(); }
  
  memcpy(buf, &data_buffer[tail * item_size], item_size);
  //*(int*)buf = *(int*)&data_buffer[tail];

  tail = (tail + 1) % size;
}


void memsocket::peek_item(char *buf, int offset) {
  
  int size = (head - tail + size) % size; 
  
  while (size <= offset) {
    sched_yield();
    size = (head - tail + size) % size; 
  }


  //*(int*)buf = *(int*)&data_buffer[ (tail + offset * item_size) % buf_size ];
  memcpy(buf, &data_buffer[ ((tail + offset) * item_size) % size ], item_size);
}



