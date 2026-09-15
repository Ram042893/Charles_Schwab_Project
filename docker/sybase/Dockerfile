FROM nguoianphu/docker-sybase:latest
ENV LD_POINTER_GUARD=0
RUN sed -i 's|-sMYSYBASE \\|-sMYSYBASE \\\n-T11889 \\|' /opt/sybase/ASE-16_0/install/RUN_MYSYBASE
