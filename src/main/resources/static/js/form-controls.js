import {enhanceFileInput} from './file-picker.js?v=0.9.1';
import {enhanceDateInput} from './date-picker.js?v=0.9.1';
let started=false;
export function initFormControls(){
  if(started)return;started=true;
  function scan(root){
    if(!(root instanceof Element)&&root!==document)return;
    const selector='input[type="file"],input[type="date"],input[type="datetime-local"],input[data-date-kind]';
    const nodes=[...(root.matches?.(selector)?[root]:[]),...root.querySelectorAll(selector)];
    for(const input of nodes){if(!input.isConnected)continue;if(input.type==='file')enhanceFileInput(input);else enhanceDateInput(input);}
  }
  scan(document);new MutationObserver(records=>{for(const record of records)for(const node of record.addedNodes)if(node.nodeType===1)scan(node);}).observe(document.body,{subtree:true,childList:true});
}
