#include "HPNL/FIStack.h"

FIStack::FIStack(const char *addr, const char *port, uint64_t flags, int worker_num_, int buffer_num_) : seq_num(0), worker_num(worker_num_), buffer_num(buffer_num_) {
  hints = fi_allocinfo();
  hints->addr_format = FI_SOCKADDR_IN;
  hints->ep_attr->type = FI_EP_MSG;
  hints->domain_attr->mr_mode = FI_MR_BASIC;
  hints->caps = FI_MSG;
  hints->mode = FI_CONTEXT | FI_LOCAL_MR;

  fi_getinfo(FI_VERSION(1, 5), addr, port, flags, hints, &info);
  fi_fabric(info->fabric_attr, &fabric, NULL);

  struct fi_eq_attr eq_attr = {
    .size = 0,
    .flags = 0,
    .wait_obj = FI_WAIT_UNSPEC,
    .signaling_vector = 0,
    .wait_set = NULL
  };
  assert(!fi_eq_open(fabric, &eq_attr, &peq, &peqHandle));

  fi_domain(fabric, info, &domain, NULL);
 
  for (int i = 0; i < worker_num; i++) {
    struct fi_cq_attr cq_attr = {
      .size = 0,
      .flags = 0,
      .format = FI_CQ_FORMAT_MSG,
      .wait_obj = FI_WAIT_FD,
      .signaling_vector = 0,
      .wait_cond = FI_CQ_COND_NONE,
      .wait_set = NULL
    };

    fi_cq_open(domain, &cq_attr, &cqs[i], NULL);
  }
}

FIStack::~FIStack() {
  for (auto iter : conMap) {
    delete iter.second; 
  }
  conMap.erase(conMap.begin(), conMap.end());
  fi_close(&peq->fid);
  for (int i = 0; i < worker_num; i++) {
    fi_close(&cqs[i]->fid); 
  }
  fi_close(&domain->fid);
  fi_close(&fabric->fid);
  fi_freeinfo(hints);
  fi_freeinfo(info);
}

HandlePtr FIStack::bind() {
  fi_passive_ep(fabric, info, &pep, NULL);  
  fi_pep_bind(pep, &peq->fid, 0);
  peqHandle.reset(new Handle(&peq->fid, EQ_EVENT, peq));
  return peqHandle;
}

void FIStack::listen() {
  fi_listen(pep);
}

HandlePtr FIStack::connect(BufMgr *recv_buf_mgr, BufMgr *send_buf_mgr) {
  FIConnection *con = new FIConnection(this, fabric, info, domain, cqs[seq_num%worker_num], waitset, recv_buf_mgr, send_buf_mgr, false, buffer_num);
  con->status = CONNECT_REQ;
  seq_num++;
  conMap.insert(std::pair<fid*, FIConnection*>(con->get_fid(), con));
  con->connect();
  return con->get_eqhandle();
}

HandlePtr FIStack::accept(void *info_, BufMgr *recv_buf_mgr, BufMgr *send_buf_mgr) {
  FIConnection *con = new FIConnection(this, fabric, (fi_info*)info_, domain, cqs[seq_num%worker_num], waitset, recv_buf_mgr, send_buf_mgr, true, buffer_num);
  con->status = ACCEPT_REQ;
  seq_num++;
  conMap.insert(std::pair<fid*, FIConnection*>(con->get_fid(), con));
  con->accept();
  return con->get_eqhandle();
}

uint64_t FIStack::reg_rma_buffer(char* buffer, uint64_t buffer_size, int rdma_buffer_id) {
  Chunk *ck = new Chunk();
  ck->buffer = buffer;
  ck->capacity = buffer_size;
  ck->rdma_buffer_id = rdma_buffer_id;
  fid_mr *mr;
  assert(!fi_mr_reg(domain, ck->buffer, ck->capacity, FI_REMOTE_READ | FI_REMOTE_WRITE | FI_SEND | FI_RECV, 0, 0, 0, &mr, NULL));
  ck->mr = mr;
  std::lock_guard<std::mutex> lk(mtx);
  chunkMap.insert(std::pair<int, Chunk*>(rdma_buffer_id, ck));
  return ((fid_mr*)ck->mr)->key;
}

void FIStack::unreg_rma_buffer(int rdma_buffer_id) {
  Chunk *ck = get_rma_chunk(rdma_buffer_id);
  fi_close(&((fid_mr*)ck->mr)->fid);
}

Chunk* FIStack::get_rma_chunk(int rdma_buffer_id) {
  std::lock_guard<std::mutex> lk(mtx);
  return chunkMap[rdma_buffer_id];
}

void FIStack::shutdown() {
  //TODO: shutdown
}

void FIStack::reap(void *con_id) {
  fid *id = (fid*)con_id;
  FIConnection *con = reinterpret_cast<FIConnection*>(get_connection(id));
  delete con;
  con = NULL;
  auto iter = conMap.find(id);
  assert(iter != conMap.end());
  conMap.erase(iter);
}

FIConnection* FIStack::get_connection(fid* id) {
  if (conMap.find(id) != conMap.end()) {
    return conMap[id]; 
  }
  return NULL;
}

fid_fabric* FIStack::get_fabric() {
  return fabric;
}

fid_cq** FIStack::get_cqs() {
  return cqs;
}

