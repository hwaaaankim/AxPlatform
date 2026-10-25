import {
  request
}
from './request.js?v=0.9.1';
import {
  message,busy
}
from './ui.js?v=0.9.1';
const status=document.getElementById('auth-status');
const recovery=document.getElementById('recovery-form'),reset=document.getElementById('reset-form');
if(recovery) {
  const syncPurpose=()=>{const password=document.getElementById('purpose').value==='PASSWORD';document.getElementById('recovery-email').hidden=!password;document.getElementById('email').disabled=!password;document.getElementById('email').required=password;message(status,'');};
  document.getElementById('purpose').addEventListener('change',syncPurpose);syncPurpose();
  recovery.addEventListener('submit',async e=> {
    e.preventDefault();
    try {
      await busy(recovery,async()=> {
        const r=await request('/api/auth/recovery', {
          method:'POST',body: {
            purpose:document.getElementById('purpose').value,email:document.getElementById('email').value,phone:document.getElementById('phone').value
          }
        }
        );
        message(status,r.message);
      }
      );
    }
    catch(e) {
      message(status,e.message,true);
    }
  }
  );
}
if(reset)reset.addEventListener('submit',async e=> {
  e.preventDefault();
  try {
    await busy(reset,async()=> {
      const r=await request('/api/auth/password/reset', {
        method:'POST',body: {
          token:reset.dataset.token,password:document.getElementById('password').value,confirmation:document.getElementById('confirmation').value
        }
      }
      );
      location.assign(r.next);
    }
    );
  }
  catch(e) {
    message(status,e.message,true);
  }
}
);
