#include <bits/stdc++.h>
using namespace std;
int main(){ios::sync_with_stdio(false);cin.tie(nullptr);
    int n,q; if(!(cin>>n>>q)) return 0; vector<char> s(n,'A');
    for(int i=0;i<q;++i){int t;cin>>t; if(t==1){int l,r;char c;cin>>l>>r>>c; for(int pos=l;pos<=r;++pos) s[pos]=c;}
        else{int k;cin>>k; long long del=0; int cnt=1; for(int j=1;j<n;++j){ if(s[j]==s[j-1]) cnt++; else cnt=1; if(cnt>k){del++; cnt--; } } cout<<del<<"\n"; }
    }
    return 0; }
